# FL Card Language — Semantic Specification

**Arquivo:** `FL_CARD_LANGUAGE_SEMANTICS.md`  
**Versão:** 0.1 — primeira especificação semântica  
**Data:** 8 de novembro de 2026  
**Projeto:** FL-OPTCG  
**Status:** `DRAFT — SEMANTICS IN PROGRESS`  
**Base normativa de trabalho:** `GAME_RULES_INVENTORY_v0.3.md`  
**Comprehensive Rules confirmado:** v1.2.1, atualizado em 28/08/2026

---

## 1. Propósito

Este documento inicia a especificação semântica da linguagem de efeitos do FL-OPTCG. Seu propósito é determinar **quais comportamentos de cartas precisam ser representáveis**, o que esses comportamentos significam segundo as regras oficiais e quais informações são necessárias para descrevê-los sem transferir para os programas das cartas responsabilidades que pertencem ao motor do jogo.

O modelo mental permanece:

- **Carta:** fornece um programa de efeitos.
- **Estado do jogo:** fornece o contexto e os dados sobre os quais os efeitos operam.
- **Motor:** aplica as regras gerais, controla o fluxo da partida e executa as consequências normativas.
- **Linguagem:** descreve as condições, escolhas, custos, operações e modificações específicas que o texto da carta determina.

A linguagem não é uma tradução da Comprehensive Rules para uma linguagem de programação. Ela é uma representação dos efeitos das cartas **dentro do ambiente de regras definido pelo motor**.

Este documento não define sintaxe TOML, opcodes, instruções de máquina, tipos concretos, serialização, módulos Kotlin ou arquitetura da VM. Também não declara que todas as capacidades aqui identificadas já estão prontas para implementação. Uma capacidade pode estar conceitualmente identificada e ainda depender de esclarecimentos normativos.

## 2. Base normativa, rastreabilidade e limites da auditoria

### 2.1 Fontes consideradas

A fonte normativa primária é a `ONE PIECE CARD GAME Comprehensive Rules`, versão 1.2.1, atualizada em 28/08/2026 (`SRC-CR`). O documento foi conferido diretamente para esta especificação, especialmente nos capítulos 1 a 4 e 6 a 11.

O inventário `GAME_RULES_INVENTORY_v0.3.md`, de 07/11/2026, é a base de organização dos achados. Ele registra as fontes adicionais:

- FAQs gerais (`SRC-GFAQ`);
- FAQs específicos de cartas, identificados por set e carta/pergunta;
- erratas oficiais e comunicados individuais (`SRC-ERRATA-HUB`, `SRC-ERRATA-EB05`, `SRC-ERRATA-OP16`, `SRC-ERRATA-OP15` e `SRC-ERRATA-OP01`).

Links oficiais de referência:

- [Comprehensive Rules v1.2.1](https://en.onepiece-cardgame.com/pdf/rule_comprehensive.pdf?20260828=)
- [Índice oficial de FAQs](https://en.onepiece-cardgame.com/rules/faq/)
- [Errata Cards](https://en.onepiece-cardgame.com/rules/errata_card/)

### 2.2 Limite explícito da auditoria

A conferência direta da Comprehensive Rules confirma a versão e os dispositivos gerais usados aqui. Para os FAQs e erratas, esta versão preserva a extração e as delimitações registradas no inventário v0.3; **não declara que todos os PDFs de FAQ, todas as respostas e todas as entradas históricas de errata foram reauditados integralmente nesta fase**.

Consequentemente:

1. Um FAQ específico fundamenta o caso que respondeu; não se torna automaticamente regra universal para todo texto parecido.
2. Uma errata altera o texto vigente da carta afetada; não cria necessariamente uma regra geral para outras cartas.
3. A ausência de um FAQ não prova que determinada interação seja impossível.
4. Quando o inventário registra `OPEN_QUESTION`, a linguagem não deve preencher a lacuna por conveniência de implementação.
5. O texto `After` de uma errata aplicável prevalece sobre o texto `Before`, conforme a fonte oficial. A cobertura integral por carta continua pendente em `Q-002`.

### 2.3 Convenção de rastreabilidade

Cada capacidade apresenta referências em três níveis:

- **CR:** seções numeradas da Comprehensive Rules, por exemplo `8-3-1-3-1`.
- **Inventário:** registros `R-xxx`, cenários `T-xxx` e questões `Q-xxx` de `GAME_RULES_INVENTORY_v0.3.md`.
- **FAQ/errata:** identificadores de fonte, carta e pergunta conforme registrados no inventário.

Os cenários `T-xxx` são evidências de comportamento a validar, não regras autônomas. A expectativa normativa continua derivando do dispositivo oficial citado.

### 2.4 Grau de cobertura normativa

A classificação abaixo descreve a **cobertura documental**, não o grau de implementação nem uma garantia de completude:

- **`HIGH`** — existe regra expressa na Comprehensive Rules para o núcleo semântico da capacidade, com cenários identificados. Ainda podem existir casos de borda não auditados.
- **`PARTIAL`** — existe fundamento geral, mas interações, timing, limites ou exceções relevantes continuam abertos; ou a cobertura depende em parte de FAQs específicos.
- **`LIMITED`** — há fundamento para um subconjunto estreito, mas não para afirmar uma capacidade geral da linguagem.
- **`OPEN`** — o material atual não permite definir com segurança a semântica necessária.

`HIGH` não significa “implementável sem investigação adicional”. A implementação ainda exigirá especificação abstrata, gramática e critérios próprios de conformidade.

## 3. Princípios semânticos fundamentais

### 3.1 Motor e programa são responsabilidades diferentes

O motor deve conhecer e aplicar as regras gerais mesmo quando nenhuma carta as menciona. Exemplos: ordem das fases; legalidade de declarar um ataque; janela em que um Blocker pode ser usado; procedimento de dano; processamento de derrota; visibilidade das áreas; aplicação de `rule processing`; ordem de prioridade de efeitos simultâneos.

O programa de uma carta declara o que aquela carta acrescenta a esse ambiente. Exemplos: ativar quando certo evento ocorrer; exigir uma condição; pagar um custo; escolher cartas segundo filtros; mover cartas; alterar Power; conceder uma keyword; substituir uma operação; manter uma modificação até determinada duração.

Um programa pode **invocar semanticamente** um procedimento do motor, mas não deve reimplementar sua regra geral. Por exemplo, um efeito pode causar dano ao Leader, mas o programa não deve reescrever o procedimento de Life, Trigger e derrota que a Comprehensive Rules associa ao dano.

### 3.2 Categorias de efeito e duração são eixos distintos

A Comprehensive Rules distingue quatro categorias de efeito:

- `auto effect`;
- `activate effect`;
- `permanent effect`;
- `replacement effect`.

Ela também distingue efeitos `one-shot` e `continuous`.

Essas classificações não devem ser fundidas. Em particular, **`permanent effect` não é sinônimo de `continuous effect`**: o primeiro é uma categoria de efeito; o segundo descreve a persistência temporal de um efeito. A especificação posterior deverá preservar ambos os eixos sem pressupor que toda combinação teoricamente possível seja válida.

### 3.3 Ativação, custo, escolha e resolução não são uma única operação

A sequência normativa pode envolver:

1. reconhecimento de uma janela de ativação;
2. verificação de condições;
3. identificação da habilidade;
4. revelação da origem, quando exigida;
5. determinação e pagamento de custos;
6. ativação;
7. escolhas feitas no momento exigido;
8. resolução ordenada das ações;
9. processamento de eventos, efeitos gerados e expirações.

Nem todos os efeitos usam todos esses passos. O motor controla os procedimentos e a linguagem representa os requisitos particulares do efeito.

### 3.4 Ações impossíveis não são todas equivalentes

É necessário distinguir, no mínimo:

- condição de ativação não satisfeita;
- custo impossível antes da ativação;
- custo que se torna impossível durante o pagamento;
- efeito ou parte de efeito inválida;
- ação de resolução impossível;
- seleção sem alvo elegível;
- cláusula `if` não resolvida;
- cláusula `then` não resolvida;
- instruções independentes que continuam executáveis.

O resultado de uma situação não pode ser inferido apenas pela frase “uma parte falhou”. A linguagem deve preservar as relações semânticas relevantes, e o motor deve aplicar as regras que determinam suas consequências.

### 3.5 Não inferir semântica a partir de semelhança textual

Expressões aparentemente próximas podem possuir momentos, quantidades e consequências diferentes. Exemplos já identificados no inventário:

- `up to X` versus quantidade fixa `X`;
- `if` versus `then`;
- DON!! `xX` dado à própria carta versus uma condição que conta DON!! dados no campo;
- `player` versus `owner`;
- `K.O.` versus enviar à Trash;
- ativar uma Event card versus ativar o efeito de uma Event;
- `base Power` versus Power efetivo;
- efeito já ativado versus auto effect ainda pendente de ativação;
- Trigger de Life versus mover Life por efeito comum.

A especificação deve representar essas distinções explicitamente. Não é permitido normalizar duas expressões para a mesma semântica apenas para reduzir o número de construções da linguagem.

## 4. Catálogo de capacidades semânticas

Os identificadores `FCL-*` são provisórios e servem apenas para rastreabilidade nesta fase. Não são nomes de opcodes, instruções, tipos ou campos TOML.

### FCL-001 — Declaração e classificação de efeitos

**Descrição do comportamento**  
Representar cada habilidade ou efeito de carta e identificar a categoria normativa a que pertence, sem confundir essa categoria com a duração ou com o conjunto de ações executadas.

**Significado semântico**  
O efeito é um comando definido pelo texto de uma carta e pelo custo associado. A linguagem precisa distinguir efeitos automáticos, efeitos ativáveis pelo jogador, efeitos permanentes e efeitos de substituição. Separadamente, precisa distinguir efeitos de execução pontual dos efeitos que continuam afetando o jogo por uma duração.

**Fundamento oficial**  
CR `2-8-1` a `2-8-3`, `4-1-1`, `8-1-1` a `8-1-4-2`, `8-2`.

**Inventário relacionado**  
`R-003`, `R-013`, `R-014`, `R-015`, `R-023`, `R-027`; `T-092` a `T-100`, `T-111` e `T-164` a `T-167`.

**Informações que precisam ser representadas**

- identidade lógica da habilidade dentro da carta;
- categoria normativa do efeito;
- se sua resolução produz uma consequência pontual ou uma consequência persistente;
- vínculo com custo, condições, timing e corpo de resolução, quando existirem;
- se há mais de uma habilidade independente no texto da carta.

**Responsabilidade do motor**  
Reconhecer as categorias e executar seus procedimentos: disparo automático, janela de ativação manual, aplicação contínua, substituição e regras de validade. O motor também determina em que áreas o texto da carta é válido, salvo exceções oficiais.

**Responsabilidade do programa da carta**  
Declarar a habilidade e descrever seu comportamento específico. A linguagem não deve codificar novamente o procedimento geral de ativação de cada categoria.

**Restrições e comportamentos especiais**

- Um efeito automático pode ativar novamente quando o evento ocorre novamente, salvo indicação contrária.
- Um efeito ativável não ganha uma janela de ativação fora das condições gerais só porque está representado no programa.
- Efeito permanente é aplicado enquanto estiver válido.
- Replacement Effect é identificado por `instead` na regra atual.
- A validade de efeitos já ativados/resolvidos e a de efeitos novos recebidos por uma carta seguem regras próprias.

**Questões não resolvidas**  
A matriz de categorias está definida no CR, mas a classificação automatizada de todas as redações históricas do catálogo ainda depende de reconciliação de cartas e erratas (`Q-002`). Não presumir que uma tradução textual classificou corretamente uma habilidade ambígua.

**Cobertura normativa:** `HIGH` para as categorias e distinções gerais; classificação de todas as cartas suportadas ainda não auditada.

### FCL-002 — Janelas de ativação e timing

**Descrição do comportamento**  
Representar em qual evento ou janela temporal uma habilidade pode ou deve ser ativada.

**Significado semântico**  
Timing identifica o acontecimento que habilita a ativação, não necessariamente uma condição permanente sobre o estado do jogo. Exemplos incluem início de turno, início de Main Phase, declaração de ataque, On Play, On Block, On K.O., dano, fim de batalha e fim de turno.

**Fundamento oficial**  
CR `6-2-2`, `6-5-1`, `6-6-1`, `7-1-1` a `7-1-5`, `8-3-1-8`, `8-6`, `10-2-5` a `10-2-8`, `10-2-15` a `10-2-17`.

**Inventário relacionado**  
`R-010`, `R-012`, `R-014`, `R-015`, `R-017`, `R-018`, `R-028`; `T-062` a `T-069`, `T-080`, `T-081`, `T-092`, `T-093`, `T-111` a `T-116`, `T-133` a `T-135`, `T-164`, `T-168` e `T-171`.

**Informações que precisam ser representadas**

- evento de ativação;
- etapa, fase ou janela relevante, quando aplicável;
- jogador ou lado do turno ao qual o timing se refere;
- relação entre o evento e a carta que gerou a habilidade;
- se a habilidade é automática ou exige declaração do jogador;
- se o timing ocorre uma vez por processamento, por evento ou conforme outra regra específica.

**Responsabilidade do motor**  
Gerar e ordenar os eventos/janelas normativos; impedir ativações fora da janela; manter a ordem de batalha e das fases; decidir quais eventos foram efetivamente ocorridos e quais efeitos pendentes ainda podem ativar.

**Responsabilidade do programa da carta**  
Declarar o timing exigido pelo texto e quaisquer restrições adicionais da habilidade.

**Restrições e comportamentos especiais**

- `When Attacking` ocorre na declaração do ataque.
- `On Your Opponent's Attack` possui ordem específica dentro do Attack Step; não pode ser reduzido a um gatilho genérico de batalha.
- `On Block` depende da ativação de Blocker.
- `On K.O.` possui sequência peculiar: ativa no campo, a carta vai para Trash e o efeito resolve na Trash.
- Efeitos gerados durante dano normalmente aguardam o fim do processamento de dano; Trigger pode interrompê-lo no ponto previsto.
- O momento exato do disparo e o momento efetivo da ativação podem ser separados. Um auto effect pode deixar de ativar se sua origem mudar de área antes da ativação.

**Questões não resolvidas**  
A cobertura de FAQ é parcial para todos os timings e suas interações concorrentes. Novas janelas e redações precisam ser confrontadas com os FAQs ainda não auditados (`Q-001`, `Q-005`, `Q-007`).

**Cobertura normativa:** `PARTIAL`.

### FCL-003 — Condições de ativação e predicados de estado

**Descrição do comportamento**  
Expressar requisitos de estado que precisam ser verdadeiros para a ativação de uma habilidade ou para a validade de uma parte do efeito.

**Significado semântico**  
Uma condição não é uma ação nem um custo. Ela consulta o estado do jogo em um momento determinado. O CR reconhece condições como `[Your Turn]`, `[Opponent's Turn]` e `[DON!! xX]`, além de cláusulas `if` e condições adicionais introduzidas por outros efeitos.

**Fundamento oficial**  
CR `2-8-2`, `3-1-2-1`, `8-3-2` a `8-3-3`, `8-4-1-1`, `8-4-6`, `10-2-9`, `10-2-11` e `10-2-12`.

**Inventário relacionado**  
`R-003`, `R-008`, `R-014`, `R-025`, `R-029`; `T-048`, `T-052`, `T-105`, `T-106`, `T-155` a `T-159`, `T-174` a `T-177`; `Q-006`, `Q-008` e `Q-012`.

**Informações que precisam ser representadas**

- predicado que deve ser satisfeito;
- objeto ou conjunto consultado;
- característica, zona, jogador ou relação consultada;
- momento de avaliação;
- se a condição bloqueia a ativação, a validade de efeito contínuo ou uma cláusula durante a resolução;
- relação entre condições múltiplas (o CR exige que todas as condições de ativação aplicáveis sejam satisfeitas).

**Responsabilidade do motor**  
Fornecer o estado correto no ponto de avaliação, aplicar as regras de característica/zona/turno e impedir ativação quando uma condição de ativação não é satisfeita.

**Responsabilidade do programa da carta**  
Declarar os predicados concretos exigidos pelo texto e indicar sua relação com o efeito ou com uma parte dele.

**Restrições e comportamentos especiais**

- Condições de ativação não são custos.
- Uma condição `[DON!! xX]` consulta os DON!! dados à própria carta que possui o efeito, conforme `8-3-2-3`; ela não é equivalente a uma condição que conta DON!! dados ao campo inteiro.
- Condições podem ser alteradas/adicionadas por outros efeitos.
- O instante de avaliação não deve ser congelado ou reavaliado continuamente por padrão. Custos e trechos anteriores podem alterar o estado visto por trechos posteriores, conforme a redação e o caso normativo.
- Condições de efeitos permanentes podem determinar se o efeito está válido.

**Questões não resolvidas**  
É necessário ampliar a auditoria de condições compostas com custo, `if`, `then`, `if you do`, DON!!, filtros de Cost/Power e efeitos contínuos (`Q-006`, `Q-008`, `Q-012`). Não há base para uma regra universal de snapshot nem para reavaliação contínua de todas as condições.

**Cobertura normativa:** `PARTIAL`.

### FCL-004 — Custos e pagamentos de ativação

**Descrição do comportamento**  
Representar pagamentos obrigatórios ou opcionais necessários para ativar uma habilidade, sem confundi-los com as operações realizadas pelo efeito depois da ativação.

**Significado semântico**  
O custo de ativação é uma sequência de ações exigida antes da resolução. Pode envolver restar DON!!, devolver DON!!, descartar cartas, restar cartas ou outras ações determinadas pelo texto. Custos de ativação são distintos do custo para jogar uma carta.

**Fundamento oficial**  
CR `1-3-9`, `8-3-1` a `8-3-1-8`, `8-4-1-3`, `10-2-13-5`.

**Inventário relacionado**  
`R-002`, `R-007`, `R-011`, `R-014`, `R-015`, `R-025`; `T-070`, `T-074`, `T-101` a `T-104`, `T-132`, `T-158`, `T-179`; FAQs `OP17-001`, `OP17-097`, `ST33-001/002/003`, `ST36-001`, `ST35-003`, `OP14-020` e `OP14-048`.

**Informações que precisam ser representadas**

- se existe custo de ativação;
- ações de pagamento que o compõem;
- sequência dos pagamentos;
- objetos/recursos que podem ser usados;
- jogador que deve pagar;
- quantidade ou condição de cada pagamento;
- opcionalidade do pagamento, quando expressa;
- substituição de custo, quando permitida por outro efeito;
- distinção entre impossibilidade anterior à ativação e impossibilidade surgida durante o pagamento.

**Responsabilidade do motor**  
Verificar se o custo pode ser pago antes da ativação; executar os pagamentos na ordem; acompanhar mudanças de estado que ocorram durante o pagamento; aplicar a regra para pagamento parcial quando a impossibilidade surgir durante o processo; tratar custo substituído segundo a regra geral.

**Responsabilidade do programa da carta**  
Declarar as ações, quantidades e condições que compõem o custo, sem codificar a regra geral de pagamento.

**Restrições e comportamentos especiais**

- Se parte do custo já é impossível antes da ativação, o custo não pode ser pago e a habilidade não ativa (`8-3-1-3`).
- Se a habilidade foi ativada e o pagamento se torna impossível no meio do processo, paga-se o máximo possível, mas o texto depois de `:` não resolve (`8-3-1-3-1`).
- Nesse último caso, `[Once Per Turn]` pode ficar consumido, mesmo sem a resolução do texto principal (`10-2-13-5`).
- Custos compostos seguem a ordem textual; custos adicionais impostos por outros efeitos seguem a ordem desses efeitos.
- Custos podem ser substituídos; a ação substituta precisa ocorrer conforme a regra aplicável.
- Não reduzir tudo a uma operação indivisível “pagar tudo ou não pagar nada”.

**Questões não resolvidas**  
A semântica geral está definida, mas a variedade de pagamentos e custos adicionais do catálogo ainda requer classificação. A interação entre custos substituídos e cada tipo de pagamento merece mais casos (`Q-003`, `Q-006`).

**Cobertura normativa:** `HIGH` para as regras gerais de pagamento; `PARTIAL` para a cobertura do repertório de custos e suas interações. Resultado global: `PARTIAL`.

### FCL-005 — Opcionalidade de efeitos e de ações

**Descrição do comportamento**  
Representar quando o jogador pode recusar ativar uma habilidade, deixar de pagar um custo opcional, escolher não executar uma ação ou escolher zero itens, sem tratar essas situações como equivalentes.

**Significado semântico**  
A opcionalidade pode incidir em níveis diferentes: ativação inteira, pagamento de custo, trecho específico, opção de substituição ou quantidade de itens escolhidos. Uma escolha `up to` pode permitir zero mesmo quando a ação não está redigida como opcional; inversamente, tornar a instrução opcional não é a mesma coisa que escolher zero itens.

**Fundamento oficial**  
CR `1-3-5-1`, `4-5-4`, `4-8`, `8-1-2`, `8-3-1-4`, `8-4-4-1`, `8-1-3-4-1`, `10-1-5-2`.

**Inventário relacionado**  
`R-002`, `R-007`, `R-014`, `R-024`, `R-026`; `T-041`, `T-042`, `T-096`, `T-107`, `T-146`, `T-153`, `T-184`, `T-185`; comunicado de errata OP-01 e erratas `OP03-054`, `OP03-047`, `OP05-032`.

**Informações que precisam ser representadas**

- escopo da opcionalidade (efeito inteiro, custo, trecho, escolha ou substituição);
- jogador autorizado a decidir;
- ponto em que a decisão deve ser tomada;
- quantidade mínima/máxima, se houver;
- consequência de recusar.

**Responsabilidade do motor**  
Apresentar escolhas apenas quando a regra permite; aplicar o valor mínimo legal; respeitar o momento normativo da decisão; não converter uma ação obrigatória em opcional.

**Responsabilidade do programa da carta**  
Declarar a opcionalidade e seu escopo exatos. A representação deve distinguir a opção de não ativar de uma seleção `up to` e de uma opção de substituição.

**Restrições e comportamentos especiais**

- `can`/`may` podem tornar a ativação ou ação opcional conforme o local e a redação.
- `up to X` permite escolher zero quando não há mínimo adicional.
- Quantidade fixa exige escolher tantas cartas/itens quanto possível, até o limite indicado.
- Recusar Replacement Effect disponível significa que a substituição não é resolvida.
- Uma errata pode mudar a opcionalidade sem alterar o restante da carta; por isso o texto corrigido é determinante.

**Questões não resolvidas**  
É necessária reconciliação completa de erratas e investigação adicional de redações combinadas (`Q-002`, `Q-005`, `Q-006`). A semântica de expressões como `if you do` não pode ser generalizada além das respostas disponíveis.

**Cobertura normativa:** `PARTIAL`.

### FCL-006 — Escolhas, seleções e alvos

**Descrição do comportamento**  
Representar a seleção de cartas, jogadores, DON!! ou outros objetos afetados por uma habilidade e separar quem escolhe de quem executa a ação.

**Significado semântico**  
Uma instrução de seleção identifica um conjunto elegível e uma decisão feita pelo jogador autorizado, no ponto exigido pela regra. A carta selecionada, o jogador que escolhe, quem realiza a movimentação e o proprietário do objeto podem ser pessoas/objetos diferentes.

**Fundamento oficial**  
CR `1-3-4`, `3-1-7`, `3-1-8`, `4-8-1`, `8-4-4` a `8-4-4-4`.

**Inventário relacionado**  
`R-007`, `R-014`, `R-024`, `R-025`, `R-026`; `T-042`, `T-043`, `T-107` a `T-109`, `T-146`, `T-147`, `T-150`, `T-154`, `T-160` a `T-163`; `Q-005`, `Q-006`.

**Informações que precisam ser representadas**

- o tipo de objeto selecionável;
- os filtros de elegibilidade;
- o número ou intervalo de itens;
- quem escolhe;
- quando a escolha é feita;
- se a escolha é obrigatória ou opcional;
- quem executa a ação após a escolha;
- o destino e o proprietário relevantes, quando houver movimentação;
- se várias escolhas/movimentações são simultâneas ou sequenciais.

**Responsabilidade do motor**  
Determinar o conjunto realmente elegível no momento correto, respeitar informação pública/privada, ordem de escolha entre jogadores e regras de propriedade. O motor também trata alvos ausentes ou ações impossíveis conforme as regras aplicáveis.

**Responsabilidade do programa da carta**  
Declarar filtros, quantidades, ator da escolha e a relação entre a escolha e a ação subsequente.

**Restrições e comportamentos especiais**

- `up to X` permite escolher zero; quantidade fixa significa escolher o máximo possível até o número.
- Escolher em área secreta com informação não verificável pode permitir recusar a escolha, nos termos de `8-4-4-2`.
- Cartas escolhidas do Deck têm suas faces verificadas.
- O proprietário pode determinar a ordem de cartas suas colocadas simultaneamente em uma área, salvo instrução diferente.
- `8-4-4-3` define um padrão somente quando o texto não especifica quem/qual objeto; não substitui instruções explícitas.
- “Alvo” não deve implicar automaticamente uma regra universal de revalidação, troca ou cancelamento se o objeto mudar de área.

**Questões não resolvidas**  
A distinção entre ator da escolha, ator da ação e proprietário ainda tem casos não cobertos (`Q-005`). Seleções ocultas, escolhas por oponente e movimentos de objetos de vários proprietários necessitam expansão de FAQs.

**Cobertura normativa:** `PARTIAL`.

### FCL-007 — Referências aos jogadores e papéis de execução

**Descrição do comportamento**  
Identificar corretamente os jogadores citados direta ou indiretamente em uma habilidade.

**Significado semântico**  
A linguagem precisa evitar que “você”, “seu oponente”, “jogador do turno”, “jogador que possui a carta”, “proprietário” e “jogador que escolhe” sejam tratados como uma única referência. O texto pode atribuir separadamente ativação, escolha, pagamento, execução, controle e recebimento de uma ação.

**Fundamento oficial**  
CR `4-2-1`, `4-2-1-1`, `4-3`, `8-4-4-3`, `1-3-4`, `1-3-10`.

**Inventário relacionado**  
`R-006`, `R-014`, `R-015`, `R-026`; `T-033`, `T-108`, `T-112`, `T-113`, `T-160` a `T-163`; `Q-005`.

**Informações que precisam ser representadas**

- jogador que controla/possui a carta no contexto da regra;
- proprietário original da carta;
- jogador do turno e não-turno;
- jogador que ativa o efeito;
- jogador que paga o custo;
- jogador que escolhe;
- jogador que executa a ação;
- jogador dono/controlador do objeto afetado;
- jogador destinatário da movimentação ou consequência.

**Responsabilidade do motor**  
Resolver os papéis com base no estado atual e nas definições oficiais; determinar a prioridade entre jogadores; aplicar regras de propriedade e de áreas.

**Responsabilidade do programa da carta**  
Declarar os papéis que o texto atribui, em vez de codificar decisões de jogador com referências ambíguas.

**Restrições e comportamentos especiais**

- `player` e `owner` não são sinônimos na Comprehensive Rules.
- Jogador que escolhe pode não ser o jogador que ativa.
- Quem escolhe pode selecionar carta da própria mão do oponente sem passar a ser seu proprietário.
- Uma instrução pode mandar o proprietário escolher qual carta sua será movida.
- O DON!! pode ser selecionado por um jogador sem que seu proprietário mude.

**Questões não resolvidas**  
A própria matriz de papéis continua incompleta em algumas combinações (`Q-005`). Não introduzir uma regra geral de “você sempre significa controlador da carta” sem verificar o contexto e os dispositivos aplicáveis.

**Cobertura normativa:** `PARTIAL`.

### FCL-008 — Referência à carta de origem e ciclo de vida da habilidade

**Descrição do comportamento**  
Preservar a relação entre uma habilidade, a carta que a originou e a situação dessa carta ao longo da ativação e da resolução.

**Significado semântico**  
A origem identifica a carta cujo texto forneceu o efeito. É necessário distinguir a carta de origem, a instância atual dessa carta, o jogador associado ao efeito e a localização da origem em momentos diferentes. O movimento da origem não possui uma consequência única para todos os tipos de efeito.

**Fundamento oficial**  
CR `2-8-2`, `3-1-6`, `8-1-3-1-3`, `8-4-4-3`, `10-1-5-3`, `10-2-17`.

**Inventário relacionado**  
`R-004`, `R-013`, `R-018`, `R-023`, `R-027`; `T-022`, `T-093`, `T-129`, `T-131`, `T-133`, `T-164` a `T-167`; `Q-008`.

**Informações que precisam ser representadas**

- carta e instância que originam a habilidade;
- efeito/habilidade originado;
- relação da origem com jogador e proprietário;
- zona da origem quando o timing ocorre, quando a ativação acontece e quando a resolução ocorre;
- se a carta foi removida, retornou ou foi tratada como nova instância;
- exceções de origem sem área durante processamento especial.

**Responsabilidade do motor**  
Manter a identidade normativa das cartas e verificar as condições de permanência da origem. O motor aplica as regras de mudança de área e as exceções de Trigger e On K.O.

**Responsabilidade do programa da carta**  
Referenciar semanticamente a própria origem quando o efeito exige isso, bem como outros objetos explicitamente indicados.

**Restrições e comportamentos especiais**

- Auto effect cujo timing ocorreu pode deixar de ativar se a origem mudar de área antes da ativação.
- Um efeito que já foi ativado não é automaticamente cancelado porque a origem mudou de área; o caso OP16-045 exemplifica resolução posterior.
- A carta que gera um Trigger pela Life não pertence a nenhuma área durante a resolução do Trigger; depois vai à Trash, salvo instrução contrária.
- On K.O. ativa no campo e resolve após a carta ser movida à Trash.
- Sair e retornar ao campo pode produzir uma nova instância para regras como `[Once Per Turn]`.

**Questões não resolvidas**  
A distinção geral está documentada, mas efeitos com movimentação da origem em outros contextos dependem de FAQs específicos ainda não auditados. Não presumir persistência ou cancelamento universal.

**Cobertura normativa:** `PARTIAL`.

### FCL-009 — Consultas ao estado e filtros de cartas

**Descrição do comportamento**  
Expressar quais objetos atendem a uma condição de busca/seleção ou quais características precisam ser consultadas por uma habilidade.

**Significado semântico**  
A habilidade pode consultar zona, categoria, nome, tipo, cor, atributo, Power, base Power, Cost, base Cost, estado, quantidade de DON!! ou relações com jogadores. A consulta deve conservar qual característica é usada e qual comparação textual é exigida.

**Fundamento oficial**  
CR `2-1` a `2-11`, `3-1-2-1`, `4-9`, `8-3-2`, `8-4-4`; regras específicas de cartas podem modificar nome/tipo/atributo inclusive em áreas secretas.

**Inventário relacionado**  
`R-003`, `R-004`, `R-008`, `R-014`, `R-026`, `R-029`, `R-030`; `T-015` a `T-021`, `T-052`, `T-105`, `T-106`, `T-108`, `T-109`, `T-174` a `T-178`; `Q-006`, `Q-008`, `Q-012`.

**Informações que precisam ser representadas**

- categoria/zona dos objetos consultados;
- características relevantes;
- uso de valor base, valor efetivo ou valor calculado;
- operador de comparação (igualdade exata, limite, intervalo, soma, negação etc.);
- conjunto sobre o qual a condição é aplicada;
- instante em que a consulta ocorre;
- se a característica é modificada por efeitos válidos no momento.

**Responsabilidade do motor**  
Fornecer características efetivas segundo a Comprehensive Rules e os efeitos válidos; respeitar áreas, informação oculta, modificadores e regras de comparação.

**Responsabilidade do programa da carta**  
Declarar o filtro e a propriedade consultada sem depender de uma interpretação textual informal.

**Restrições e comportamentos especiais**

- `with 8000 power`, `base power 8000`, `cost 4 or less` e soma de Costs não são filtros intercambiáveis.
- Power pode ser negativo; Cost efetivo não permanece negativo fora dos cálculos permitidos.
- Nome/tipo/atributo concedidos por texto podem afetar consultas em áreas secretas quando o CR prevê isso.
- A regra-padrão `field` para expressões como “if you have” deve ser observada salvo especificação diferente.
- A soma de características selecionadas deve ser aplicada ao conjunto quando a redação/FAQ assim determinar; não decompor um limite agregado em limites individuais.
- Efeitos restritos a uma zona não podem ser aplicados globalmente.

**Questões não resolvidas**  
A auditoria de comparadores exatos, base/original/efetivo, zonas secretas e efeitos que afetam cartas futuras segue aberta (`Q-006`, `Q-008`, `Q-012`).

**Cobertura normativa:** `PARTIAL`.

### FCL-010 — Operações de movimentação entre áreas

**Descrição do comportamento**  
Representar a movimentação de cartas entre as áreas oficiais, sem assumir que toda movimentação produz as mesmas consequências.

**Significado semântico**  
O programa declara quais cartas serão movidas, a origem, o destino, a quantidade, a ordem e o agente responsável. O motor aplica as regras de cada área e os efeitos colaterais normativos da mudança de área.

**Fundamento oficial**  
CR `3-1` a `3-10`, `4-5`, `4-6`, `4-7`, `4-11`, `8-4-5`, `10-2-1`, `10-2-14`.

**Inventário relacionado**  
`R-004`, `R-005`, `R-007`, `R-008`, `R-017`, `R-018`, `R-023`, `R-026`; `T-022` a `T-032`, `T-039` a `T-047`, `T-053`, `T-126`, `T-134`, `T-139`, `T-140`, `T-164` a `T-167`.

**Informações que precisam ser representadas**

- objeto ou conjunto movido;
- área de origem e destino;
- jogador/owner de cada objeto;
- quantidade;
- posição no destino (topo, fundo ou outra posição definida);
- ordem quando várias cartas são movidas;
- condição ou motivo específico da movimentação;
- estado/revelação no destino quando isso for parte do efeito;
- simultaneidade ou sequência.

**Responsabilidade do motor**  
Executar as regras de zonas, limites, visibilidade, identidade de nova instância, retorno de DON!! anexado, ordenação e processamento de eventos gerados pela movimentação.

**Responsabilidade do programa da carta**  
Declarar origem/destino, conjunto, quantidade, posição e condições especiais da movimentação.

**Restrições e comportamentos especiais**

- `K.O.` não é sinônimo de `Trash` ou `Remove`.
- O Character enviado à Trash pelo limite de cinco não é K.O.; nenhuma habilidade deve ser disparada por esse K.O. inexistente.
- Sair da Character/Stage Area normalmente cria uma nova carta em nova área para fins de efeitos aplicados anteriormente.
- DON!! anexado retorna à Cost Area rested quando sua carta hospedeira muda de área.
- Mover uma carta entre duas áreas secretas exige revelação durante o procedimento, conforme a regra.
- Mover cartas de Life segue as restrições da Life Area e não equivale automaticamente a causar dano.

**Questões não resolvidas**  
Há interações não auditadas para movimentações simultâneas, movimentos de vários proprietários, zonas secretas e efeitos que dependem do destino (`Q-003`, `Q-004`, `Q-005`, `Q-008`).

**Cobertura normativa:** `PARTIAL`.

### FCL-011 — Informação oculta, olhar, revelar e embaralhar

**Descrição do comportamento**  
Representar operações que consultam, revelam, procuram ou reorganizam cartas em áreas secretas.

**Significado semântico**  
Olhar uma área secreta não é o mesmo que mover cartas dessa área. Revelar uma carta não significa necessariamente movê-la. Buscar uma carta e movê-la para outra área secreta requer o procedimento de revelação previsto nas regras. O estado e a ordem originais podem precisar ser restaurados.

**Fundamento oficial**  
CR `3-2`, `3-4`, `3-10`, `8-4-4-2`, `8-4-4-4`, `11-2`, `11-3`.

**Inventário relacionado**  
`R-004`, `R-007`, `R-014`, `R-019`, `R-026`, `R-027`; `T-025` a `T-028`, `T-045` a `T-047`, `T-109`, `T-139`, `T-140`, `T-147`; `Q-008`.

**Informações que precisam ser representadas**

- área consultada;
- jogador autorizado a olhar;
- quantidade ou limite de cartas examinadas;
- se o procedimento é olhar, revelar, selecionar, mover ou embaralhar;
- duração da revelação;
- estado e ordem a restaurar quando exigido;
- filtro aplicado depois da consulta;
- consequência para cartas não selecionadas.

**Responsabilidade do motor**  
Controlar informação pública/privada; impedir consultas não autorizadas; revelar cartas quando exigido; restaurar visibilidade/ordem; executar embaralhamento e manter a integridade da área.

**Responsabilidade do programa da carta**  
Declarar qual área pode ser consultada, quantos objetos podem ser vistos e quais decisões/ações são realizadas sobre eles.

**Restrições e comportamentos especiais**

- Cartas permanecem em sua área original enquanto são apenas olhadas.
- Se não houver instrução posterior sobre as cartas vistas, elas retornam à área, ordem e estado originais.
- Cartas movidas de área secreta para outra área secreta precisam ser reveladas no procedimento.
- Cartas do Deck escolhidas por seleção são verificadas.
- Uma escolha em área secreta que depende de informação não disponível pode ser recusada de acordo com `8-4-4-2`.
- Life face-up possui tratamento próprio; não pode ser simplesmente equiparada à mão ou ao Deck.

**Questões não resolvidas**  
Efeitos permanentes/nominais válidos em áreas secretas e redações que consultam cartas no Deck, Hand ou Life continuam pendentes (`Q-008`).

**Cobertura normativa:** `PARTIAL`.

### FCL-012 — Operações e condições envolvendo DON!!

**Descrição do comportamento**  
Representar a seleção, pagamento, concessão, retirada e contagem de DON!!, preservando os diferentes significados que aparecem no texto das cartas.

**Significado semântico**  
DON!! pode estar no DON!! Deck, Cost Area ou dado a Leader/Character. Esses estados não são equivalentes. O programa pode requerer uma quantidade anexada à própria carta, uma quantidade agregada no campo, um pagamento com DON!! ativo ou a retirada de DON!! de várias áreas permitidas.

**Fundamento oficial**  
CR `4-4-2`, `6-2-3`, `6-5-5`, `8-3-1-5`, `8-3-1-6`, `8-3-2-3`, `10-2-9`, `10-2-10`.

**Inventário relacionado**  
`R-006`, `R-011`, `R-014`, `R-017`, `R-026`, `R-028`; `T-034` a `T-038`, `T-070` a `T-075`, `T-106`, `T-161`, `T-163`, `T-168` a `T-170`; FAQs `ST31-003`, `OP16-074`, `OP15-023`.

**Informações que precisam ser representadas**

- seleção de DON!! por área;
- estado active/rested quando aplicável;
- se o DON!! está dado a uma carta;
- carta hospedeira, quando houver;
- quantidade exata, mínima ou agregada;
- destino (Cost Area ou DON!! Deck);
- finalidade: custo, condição, bônus, retirada ou outra instrução;
- jogador/proprietário do DON!!.

**Responsabilidade do motor**  
Manter as áreas/associações de DON!!, aplicar o bônus de Power durante o turno, executar Refresh, validar estados permitidos e devolver DON!! anexado quando a hospedeira muda de área.

**Responsabilidade do programa da carta**  
Declarar as condições e operações específicas sobre DON!!, incluindo qual conjunto está sendo contado ou selecionado.

**Restrições e comportamentos especiais**

- DON!! dado não é active nem rested.
- DON!! `xX` consulta a quantidade dada à própria carta com a habilidade.
- Uma condição textual como “total of 3 or more DON!! cards given” pode contar DON!! dados a vários objetos, conforme o caso oficial; não é a mesma condição `[DON!! xX]`.
- DON!! `−X` permite seleção a partir das áreas indicadas no CR, incluindo DON!! dado.
- Um DON!! já dado não pode ser transferido diretamente para outra carta.
- DON!! dado que retorna por Refresh vai à Cost Area rested.
- A regra de seleção por proprietário precisa ser preservada mesmo quando o oponente seleciona.

**Questões não resolvidas**  
É necessário catalogar expressões de contagem agregada, DON!! de oponente, remoção de DON!! por efeitos e seus impactos em custos/condições (`Q-003`, `Q-005`, `Q-006`).

**Cobertura normativa:** `PARTIAL`.

### FCL-013 — Ações elementares sobre objetos do jogo

**Descrição do comportamento**  
Identificar as famílias de ações que um efeito pode ordenar, sem criar uma operação exclusiva para cada carta.

**Significado semântico**  
A linguagem precisa expressar ações recorrentes de jogo, como comprar, descartar/trash, K.O., mover entre áreas, restar, tornar active, adicionar à Life, jogar carta, devolver carta e modificar estado. O inventário desta fase não fecha a lista definitiva nem determina nomes de instruções.

**Fundamento oficial**  
CR `1-3-2`, `1-3-7`, `4-5`, `4-6`, `4-7`, `4-11`, `6-5`, `7-1-4`, `10-2-1`, `10-2-14`.

**Inventário relacionado**  
`R-002`, `R-004`, `R-005`, `R-007`, `R-008`, `R-011`, `R-017`, `R-024`, `R-025`; `T-008` a `T-014`, `T-029` a `T-053`, `T-070` a `T-075`, `T-126`, `T-149` e `T-155` a `T-159`.

**Informações que precisam ser representadas**

- ação semântica pretendida;
- objeto ou conjunto de objetos;
- quantidade;
- pré-condições específicas;
- jogador que executa;
- destino/estado final;
- opcionalidade;
- relação com outras ações;
- comportamento quando impossível.

**Responsabilidade do motor**  
Executar os procedimentos oficiais associados a cada ação e impedir ações proibidas ou impossíveis. O motor distingue a movimentação comum de K.O., dano, jogo de carta, pagamento e outras operações regulamentadas.

**Responsabilidade do programa da carta**  
Escolher a ação semântica apropriada e declarar os parâmetros específicos. A linguagem não deve oferecer “efeito da carta X” como ação especial.

**Restrições e comportamentos especiais**

- “Trash” tem semântica própria em diferentes contextos; `10-2-14` define Trash de carta da mão, enquanto outras movimentações têm origem/destino diferentes.
- K.O. é restrito a Character e possui eventos/proteções próprios.
- Jogar uma carta não é uma simples movimentação para campo: envolve custo, categoria, limites e procedimento de jogo.
- Draw tem procedimento específico e não revela carta.
- Ações impossíveis individuais não impedem automaticamente outras ações possíveis.

**Questões não resolvidas**  
O inventário oferece exemplos suficientes para reconhecer famílias, mas não constitui um catálogo completo de todas as ações de texto. A definição final do vocabulário deve aguardar o fechamento das capacidades e a auditoria do catálogo real.

**Cobertura normativa:** `PARTIAL`.

### FCL-014 — Modificações de características

**Descrição do comportamento**  
Representar alterações de características de cartas, tanto pontuais como persistentes, sem reduzir todas as modificações a um único tipo de bônus.

**Significado semântico**  
Uma habilidade pode aumentar, reduzir, definir ou conceder características. Entre as propriedades relevantes estão Power, base Power, Cost, base Cost, Life, Counter, nome, tipo, cor, atributo e keywords. O significado de cada modificação depende da propriedade e do momento.

**Fundamento oficial**  
CR `1-3-6`, `2-1` a `2-11`, `4-9`, `4-12`, `8-1-4`, `10-2-13`; a regra de Counter mais alto consta também de `2-10-4` na versão v1.2.1 conferida.

**Inventário relacionado**  
`R-003`, `R-008`, `R-029`, `R-030`; `T-015` a `T-021`, `T-050`, `T-051`, `T-172` a `T-178`; FAQs `ST34-004`, `OP14-001`, `OP17-008/085/097/119`, `OP16-002/003/007/008`, `EB05-010`.

**Informações que precisam ser representadas**

- característica modificada;
- operação semântica (aumentar, reduzir, definir, conceder, remover etc.);
- valor ou expressão que determina a alteração;
- se incide sobre base, valor efetivo ou texto/característica categórica;
- objeto afetado;
- condição de aplicação;
- duração;
- momento em que um valor é capturado, se a regra/texto assim exigir.

**Responsabilidade do motor**  
Calcular características efetivas segundo as regras de precedência, limites e camadas de efeito; aplicar valores negativos quando permitido; recalcular quando necessário; respeitar o escopo temporal e a identidade da carta.

**Responsabilidade do programa da carta**  
Declarar a característica, a modificação, o alvo e a duração/condição determinada pela carta.

**Restrições e comportamentos especiais**

- Power pode ser negativo e isso não move automaticamente a carta para outra área.
- Cost efetivo é tratado como zero fora dos cálculos quando ficaria negativo; durante cálculos, o valor negativo pode ser relevante.
- Quando vários efeitos definem base Power/base Cost e incidem na mesma carta, o CR estabelece a precedência do maior valor.
- Base Power e Power efetivo são conceitos distintos. Um modificador de Power pode continuar incidindo sobre um novo base Power.
- `Set Power to 0` usa o Power existente no instante normativo definido pelo CR; não é simplesmente atribuir valor final zero em todos os casos.
- Counter possui regra própria: quando há múltiplos valores de Counter em uma carta, aplica-se o mais alto; a FAQ OP17-063/118 trata também de modificadores similares aplicáveis durante Counter Step.
- Valores exatos, como `with 8000 power`, não podem ser convertidos automaticamente em `at least 8000`.

**Questões não resolvidas**  
A cobertura de snapshot, duração e escopo de modificadores precisa ser ampliada (`Q-006`, `Q-008`, `Q-012`). A interação de efeitos que concedem/removem keywords pode variar conforme a origem da concessão (`EB05-010`).

**Cobertura normativa:** `PARTIAL`.

### FCL-015 — Keywords e efeitos de keyword

**Descrição do comportamento**  
Representar keywords definidas pelas regras sem reimplementar seus procedimentos gerais como se fossem comportamentos exclusivos de cada carta.

**Significado semântico**  
As keywords são capacidades com significado oficial. Algumas definem permissões de ataque ou etapas da batalha; outras modificam dano; outras estabelecem timing ou condições; outras representam instruções como K.O. ou Trash. A linguagem pode precisar declarar que uma carta possui, ganha, perde ou modifica uma keyword, mas o significado operacional permanece no motor.

**Fundamento oficial**  
CR capítulo 10, especialmente `10-1-1` a `10-1-7` e `10-2-1` a `10-2-17`.

**Inventário relacionado**  
`R-012`, `R-017`, `R-018`, `R-028`, `R-029`; `T-120` a `T-135`, `T-168` a `T-171`; FAQ `EB05-010` e FAQs de Blocker/Counter/Trigger referenciados em `R-012`.

**Informações que precisam ser representadas**

- keyword semântica;
- carta à qual se aplica;
- origem da keyword (impressa ou concedida por efeito, quando isso for relevante);
- condição e duração da concessão;
- eventual restrição/variante expressa no texto;
- relação da keyword com timing ou procedimento do motor.

**Responsabilidade do motor**  
Implementar os significados oficiais de Rush, Double Attack, Banish, Blocker, Trigger, Rush: Character, Unblockable, K.O., Activate: Main, Main, Counter, When Attacking, On Play, End of Your Turn, End of Your Opponent’s Turn, DON!! xX, DON!! −X, Your Turn, Opponent’s Turn, Once Per Turn, Trash, On Block, On Your Opponent’s Attack e On K.O.

**Responsabilidade do programa da carta**  
Declarar a presença, concessão, remoção ou condição da keyword quando isso decorrer do texto da carta. O programa não deve reproduzir o procedimento geral que a keyword já determina.

**Restrições e comportamentos especiais**

- Rush e Rush: Character não concedem a mesma permissão de alvo.
- Double Attack altera a quantidade de dano de ataque ao Leader, não cria ataques independentes.
- Banish altera o destino do dano de Life e impede Trigger naquele procedimento.
- Blocker ocorre em Block Step, respeita limite por batalha e altera o alvo do ataque.
- Unblockable impede Blocker contra o ataque especificado.
- Trigger depende do procedimento de dano e tem ciclo de vida especial para a carta de origem.
- K.O. não é sinônimo de qualquer envio à Trash.
- `Counter` só pode ser ativado nas janelas próprias, com exceções normativas expressas.
- A origem de uma keyword concedida pode importar; não presumir que todas as concessões se comportem igual.

**Questões não resolvidas**  
Embora as keywords listadas estejam definidas pelo CR, a aplicação de keywords concedidas, negadas ou modificadas por efeitos contínuos requer mais auditoria. O FAQ EB05-010 demonstra que a origem da concessão pode ser relevante.

**Cobertura normativa:** `PARTIAL` para o domínio amplo; `HIGH` para o significado básico das keywords expressamente definidas no CR.

### FCL-016 — Efeitos permanentes e modificadores contínuos

**Descrição do comportamento**  
Representar efeitos que modificam continuamente o jogo enquanto estiverem válidos, inclusive quando dependem de condições e quando alteram características de cartas presentes ou futuras.

**Significado semântico**  
Um efeito permanente permanece influenciando o jogo enquanto válido. Um efeito contínuo permanece por uma duração. A linguagem precisa indicar o conteúdo da alteração e suas condições/escopo; o motor determina validade, prioridade, aplicação e estabilização.

**Fundamento oficial**  
CR `8-1-3-3` a `8-1-3-3-5`, `8-1-4-2`, `8-4-6`, além das regras de duração de `6-2`, `6-6` e `7-1-5`.

**Inventário relacionado**  
`R-013`, `R-029`; `T-095`, `T-099`, `T-100`, `T-172` a `T-178`; `Q-008`, `Q-012`.

**Informações que precisam ser representadas**

- efeito permanente ou contínuo;
- condição de validade;
- objetos e zonas afetados;
- característica ou regra afetada;
- duração, quando aplicável;
- comportamento para objetos que entrarem no conjunto afetado posteriormente;
- relação com outros modificadores permanentes;
- se a aplicação depende de estado atual ou de condição histórica definida pelo texto.

**Responsabilidade do motor**  
Manter a validade dos efeitos; aplicar os efeitos permanentes conforme prioridade do jogador do turno e do não-turno; repetir o processamento até estabilização quando a regra exigir; considerar efeitos contínuos já resolvidos e efeitos permanentes válidos ao ativar/resolver uma habilidade.

**Responsabilidade do programa da carta**  
Declarar a alteração e seu conjunto de aplicação, condição e duração, sem implementar o algoritmo de estabilização do CR.

**Restrições e comportamentos especiais**

- “Enquanto esta carta estiver no campo” não equivale a efeito que alcance uma carta em qualquer zona.
- Efeitos `according to/under the rules` possuem exceção de validade em áreas secretas.
- Algumas FAQs estabelecem que um efeito não afeta Characters jogados depois; outras situações podem ser dinâmicas. Não usar snapshot universal.
- Um efeito já resolvido e persistente não é cancelado simplesmente porque a carta de origem mudou de área, salvo regra/texto específico.

**Questões não resolvidas**  
A semântica de aplicação a objetos futuros, snapshots, reavaliação de condições e efeitos em zonas secretas continua aberta (`Q-008`, `Q-012`).

**Cobertura normativa:** `PARTIAL`.

### FCL-017 — Replacement Effects

**Descrição do comportamento**  
Representar efeitos que substituem uma situação ou parte de um processamento por outra, normalmente identificados por `instead`.

**Significado semântico**  
Uma substituição intercepta o processamento indicado e executa o procedimento substituto. Ela não necessariamente cancela a habilidade inteira que estava sendo resolvida. A substituição pode ser opcional, ter custo, afetar uma operação coletiva e habilitar outra substituição.

**Fundamento oficial**  
CR `8-1-3-4-1` a `8-1-3-4-7`, especialmente `8-1-3-4-4-1`.

**Inventário relacionado**  
`R-013`, `R-024`; `T-096` a `T-098`, `T-149` a `T-154`; `Q-003`, `Q-004`, `Q-007`, `Q-013`. FAQs `OP17-015/021/023/095` e `OP15-009/035/052/069/090/094/098/105`.

**Informações que precisam ser representadas**

- tipo de situação/processamento que pode ser substituído;
- condição para substituição;
- trecho/processo abrangido;
- procedimento substituto;
- custo e sua quantidade;
- opcionalidade;
- jogadores que controlam a substituição;
- relação com outras substituições aplicáveis;
- escopo individual ou coletivo do evento substituído.

**Responsabilidade do motor**  
Detectar situações substituíveis; aplicar a prioridade oficial; impedir reaplicação repetida da mesma substituição ao mesmo processamento; verificar se o procedimento substituto é possível; processar substituições encadeadas e continuar o restante do texto quando exigido.

**Responsabilidade do programa da carta**  
Declarar o evento/processamento que pretende substituir, a condição, o custo e a alternativa. Não codificar a fila geral de prioridade.

**Restrições e comportamentos especiais**

- `instead` identifica Replacement Effect.
- A substituição da carta que gerou aquele Replacement Effect tem precedência conforme `8-1-3-4-2`; depois vêm as substituições do jogador do turno e do não-turno, na ordem escolhida por cada um.
- A substituição troca toda a parte indicada pelo texto, mesmo no meio da resolução; o texto restante continua em ordem conforme `8-1-3-4-4-1`.
- A mesma substituição não pode substituir repetidamente a mesma situação/processamento individual.
- Substituição impossível não pode ser aplicada; substituição recusada não resolve.
- Uma substituição pode habilitar outra.
- FAQs mostram que uma única ação de pagamento pode, em casos específicos, impedir a saída simultânea de mais de uma carta. Não inferir regra geral de quantos objetos são salvos ou quantos pagamentos são exigidos.

**Questões não resolvidas**  
A composição de substituições concorrentes/encadeadas e a abrangência coletiva ainda não estão completamente cobertas (`Q-003`, `Q-004`). Dano, zero Life e derrota com substituições encadeadas exigem novos casos (`Q-007`, `Q-013`).

**Cobertura normativa:** `PARTIAL`.

### FCL-018 — Sequenciamento, cláusulas condicionais e ramificações

**Descrição do comportamento**  
Representar a ordem das ações e as dependências entre trechos de um efeito.

**Significado semântico**  
As instruções de um efeito normalmente são processadas na ordem do texto. Algumas cláusulas condicionam trechos posteriores; outras apenas organizam a sequência. A linguagem precisa preservar a diferença entre `if`, `then`, opções e instruções independentes.

**Fundamento oficial**  
CR `1-3-2`, `1-3-7`, `4-10-1`, `4-10-2`, `8-3-3`, `8-4-1`.

**Inventário relacionado**  
`R-002`, `R-008`, `R-014`, `R-015`, `R-025`; `T-008`, `T-014`, `T-048`, `T-049`, `T-052`, `T-103`, `T-104`, `T-149`, `T-155` a `T-159`; `Q-006`.

**Informações que precisam ser representadas**

- ordem das ações;
- dependência entre cláusulas;
- condição associada a uma cláusula;
- alternativas mutuamente exclusivas, quando o texto estabelece opções;
- ponto em que uma condição é avaliada;
- quais ações são independentes;
- consequência da impossibilidade de uma ação anterior.

**Responsabilidade do motor**  
Executar o corpo do efeito na ordem normativa; aplicar a regra de ação impossível; determinar os resultados das condições no momento correto; respeitar interrupções por Replacement Effects, Trigger ou `rule processing`.

**Responsabilidade do programa da carta**  
Descrever a sequência e as dependências expressas pelo texto, sem transformar o efeito em uma cadeia de falha global não autorizada pelas regras.

**Restrições e comportamentos especiais**

- Uma cláusula `if` precedente não resolvida impede a cláusula seguinte nos termos de `4-10-1`.
- Uma cláusula `then` precedente não resolvida não impede automaticamente a seguinte nos termos de `4-10-2`, salvo a dependência de `if` indicada pela regra.
- Ações impossíveis podem ser ignoradas individualmente quando o efeito tem várias ações, mas isso não apaga dependências explícitas.
- Uma ação de custo não é uma ação comum do corpo de resolução.
- A ausência de alvo de uma parte não cancela necessariamente outra parte independente.
- `if you do` e construções semelhantes precisam de análise própria; não se deve assumir que sempre se comportam como `then`.

**Questões não resolvidas**  
A dependência semântica de `if you do`, orações compostas e condicionais após custo/seleção ainda necessita de mais FAQs (`Q-006`).

**Cobertura normativa:** `PARTIAL`.

### FCL-019 — Eventos, gatilhos e encadeamento de efeitos

**Descrição do comportamento**  
Representar o vínculo entre acontecimentos do jogo e habilidades que passam a poder ser ativadas em consequência deles.

**Significado semântico**  
Um evento do jogo pode satisfazer timings de várias habilidades. O motor precisa reconhecer quais efeitos foram habilitados e processá-los na ordem oficial. A linguagem declara quais eventos interessam a cada habilidade e quais condições adicionais se aplicam.

**Fundamento oficial**  
CR `8-1-3-1`, `8-3-1-8`, `8-4-5`, `8-6-1` a `8-6-3`, `9-1`, `10-2`.

**Inventário relacionado**  
`R-012`, `R-015`, `R-016`, `R-017`, `R-018`, `R-023`, `R-027`, `R-028`; `T-080` a `T-081`, `T-092` a `T-093`, `T-111` a `T-119`, `T-125`, `T-133` a `T-135`, `T-164` a `T-171`.

**Informações que precisam ser representadas**

- evento que habilita o efeito;
- contexto do evento (carta, jogador, batalha, zona, alvo e quantidade, quando relevante);
- timing de ativação;
- condição adicional;
- efeitos concorrentes gerados pelo mesmo evento;
- eventos gerados durante a resolução de outro efeito;
- interrupções excepcionais;
- relação entre ativação do evento e ativação/resolução da habilidade.

**Responsabilidade do motor**  
Gerar eventos normativos, identificar efeitos elegíveis e controlar sua fila/ordem de processamento. O motor também deve separar `rule processing` da resolução de efeitos e distinguir ativação de carta da ativação de efeito.

**Responsabilidade do programa da carta**  
Declarar os eventos de interesse e os filtros contextuais necessários.

**Restrições e comportamentos especiais**

- Efeitos simultâneos dos dois jogadores seguem a prioridade do jogador do turno, com a regra de interrupção/encadeamento de `8-6-1`.
- Eventos gerados durante dano normalmente aguardam o fim do processamento de dano, exceto o procedimento de Trigger.
- Efeitos gerados pela ativação de uma carta/efeito têm timing próprio após a resolução da carta/efeito anterior.
- Auto effects gerados por mudança de área dependem de área de destino aberta conforme `8-4-5`.
- Ativar Event da mão não é o mesmo que ativar um efeito `[Main]` de Event por outro efeito ou resolver seu Trigger.

**Questões não resolvidas**  
A auditoria não esgotou a ordem de todos os eventos encadeados, particularmente em danos, efeitos simultâneos e movimentações em massa (`Q-001`, `Q-003`, `Q-007`).

**Cobertura normativa:** `PARTIAL`.

### FCL-020 — Multiplicidade, quantidades e repetição

**Descrição do comportamento**  
Representar operações que ocorrem uma ou várias vezes, com quantidades fixas, máximos, escolhas repetidas ou danos múltiplos.

**Significado semântico**  
A quantidade de vezes, a quantidade de objetos e o número de repetições escolhidas são dimensões distintas. O CR define procedimentos especiais para Draw, dano, `up to`, ações com quantidade zero/negativa e loops infinitos.

**Fundamento oficial**  
CR `1-3-2-2`, `1-3-5`, `4-5-3`, `4-5-4`, `4-6-2-2`, `4-8`, `8-1-3-1`, `10-1-2`, `11-1`.

**Inventário relacionado**  
`R-002`, `R-007`, `R-012`, `R-014`, `R-019`, `R-024`, `R-025`; `T-010`, `T-039` a `T-042`, `T-045`, `T-047`, `T-088` a `T-090`, `T-107`, `T-146`, `T-149` a `T-154`, `T-136` a `T-138`; `Q-004`, `Q-007`, `Q-010`.

**Informações que precisam ser representadas**

- quantidade ou expressão que determina a quantidade;
- unidade da repetição (cartas, ações, danos, eventos etc.);
- quantidade fixa ou intervalo;
- quem escolhe a quantidade;
- momento da escolha;
- se as repetições podem terminar antes do limite;
- se cada repetição abre uma janela/evento individual;
- se a instrução é sequencial ou simultânea.

**Responsabilidade do motor**  
Aplicar limites legais, regras para zero/negativo, procedimentos especiais de Draw/dano e política oficial para loops infinitos. Manter o processamento de cada dano/Trigger na ordem prevista.

**Responsabilidade do programa da carta**  
Declarar a quantidade, limite, fórmula ou repetição expressa no efeito. Não introduzir loops arbitrários para simular qualquer comportamento.

**Restrições e comportamentos especiais**

- Número zero ou negativo não executa a ação.
- `Draw X` repete o procedimento X vezes; `Draw up to X` permite encerrar antes de cada compra.
- `up to X` permite escolher zero, observando o momento de escolha da regra aplicável.
- Dano múltiplo não deve ser tratado como vários ataques independentes; Trigger pode suspender o processamento entre danos.
- Ações simultâneas de vários objetos não são necessariamente equivalentes a uma sequência de ações individuais.
- A Comprehensive Rules define como lidar com loops infinitos; isso não significa que a linguagem precise ser uma linguagem geral com laços ilimitados.

**Questões não resolvidas**  
A variedade de expressões repetitivas e loops de cartas concretas não está inventariada por completo (`Q-004`, `Q-007`, `Q-010`).

**Cobertura normativa:** `PARTIAL`.

### FCL-021 — Duração, escopo e expiração

**Descrição do comportamento**  
Representar por quanto tempo e sobre quais objetos uma modificação/efeito permanece válida.

**Significado semântico**  
Duração determina quando o efeito deixa de afetar o jogo. Escopo determina quais cartas, jogadores, batalhas, zonas ou conjuntos são afetados. Um efeito que já resolveu pode persistir apesar de a carta de origem mudar de área; já um efeito aplicado à instância da carta que saiu pode deixar de acompanhá-la.

**Fundamento oficial**  
CR `3-1-6`, `6-2-1`, `6-6-1-2` a `6-6-1-4`, `7-1-5-3` a `7-1-5-4`, `8-1-4-2`, `10-2-13`.

**Inventário relacionado**  
`R-004`, `R-010`, `R-012`, `R-013`, `R-018`, `R-029`; `T-022`, `T-063`, `T-069`, `T-071`, `T-095`, `T-099`, `T-100`, `T-129`, `T-131`, `T-172` a `T-175`; `Q-012`.

**Informações que precisam ser representadas**

- início da duração;
- evento/momento de expiração;
- escopo (carta, instância, jogador, campo, batalha ou conjunto definido);
- condição de validade;
- relação entre duração e mudança de área da origem;
- jogador proprietário do efeito para fins de processamento de expiração;
- prioridade de expiração quando aplicável.

**Responsabilidade do motor**  
Controlar relógios/eventos de turno e batalha; expirar efeitos na ordem oficial; separar efeito aplicado à carta de efeito já criado; remover alterações de cartas/objetos quando regras de mudança de área assim determinarem.

**Responsabilidade do programa da carta**  
Declarar o escopo e a duração expressos pelo texto.

**Restrições e comportamentos especiais**

- `during this turn`, `until the end of this turn`, `until the end of the End Phase`, `during this battle` e `until the start of your next turn` não devem ser colapsados sem análise.
- O End Phase possui ordem específica de ativação e expiração.
- Efeitos `during this battle` expiram no fim da batalha seguindo a ordem do jogador do turno e depois do não-turno.
- Efeito já resolvido com duração de turno pode continuar após a origem mudar de área, conforme o caso FAQ registrado.
- Mudança de área normalmente remove efeitos aplicados à instância anterior da carta.

**Questões não resolvidas**  
A extensão temporal e o escopo de modificadores de Cost/Power e características continuam sujeitos a exemplos adicionais (`Q-012`).

**Cobertura normativa:** `PARTIAL`.

### FCL-022 — Interação com ataques e batalhas

**Descrição do comportamento**  
Representar os efeitos de cartas que concedem, restringem ou alteram permissões e elementos de uma batalha, sem delegar à carta a implementação da sequência geral da batalha.

**Significado semântico**  
Ataque e batalha são procedimentos do motor. Um programa pode alterar requisitos de ataque, permitir tipos específicos de alvo, alterar/redirecionar o alvo, reagir à declaração, afetar o Blocker ou modificar Power durante uma batalha.

**Fundamento oficial**  
CR capítulo 7, `10-1-1`, `10-1-4`, `10-1-6`, `10-1-7`, `10-2-5`, `10-2-15`, `10-2-16`.

**Inventário relacionado**  
`R-012`, `R-017`, `R-028`; `T-076` a `T-087`, `T-120`, `T-121`, `T-123`, `T-124`, `T-168` a `T-171`; FAQs `OP16-080`, `ST31-003`, `ST32-005`, Promotion Cards `P-001/P-004`, `OP17-040`.

**Informações que precisam ser representadas**

- atacante e alvo;
- permissões/restrições de atacar;
- condição da carta para atacar no turno em que foi jogada;
- efeitos disparados pela declaração;
- redirecionamento de alvo;
- concessão/negação de Blocker;
- modificações válidas durante a batalha;
- duração e persistência de mudanças já realizadas.

**Responsabilidade do motor**  
Executar Attack, Block, Counter, Damage e End of Battle Steps; validar a legalidade de ataque/alvo; controlar a declaração, o uso único de Blocker, a comparação de Power e o encerramento antecipado da batalha.

**Responsabilidade do programa da carta**  
Declarar permissões, restrições, modificações de alvo e respostas a eventos de batalha.

**Restrições e comportamentos especiais**

- O alvo normal é Leader do oponente ou Character rested do oponente.
- Rush e Rush: Character têm permissões diferentes.
- Um ataque já declarado não é desfeito automaticamente porque uma condição posterior mudou.
- Um Blocker que substituiu o alvo não necessariamente devolve o ataque ao alvo original se sua condição mudar depois.
- Se atacante ou alvo sair de área durante Attack, Block ou Counter Step, a batalha pode terminar sem avançar à etapa seguinte, segundo o CR.
- A condição de ativação de Blocker pode ser avaliada após efeitos anteriores da etapa; não pode ser congelada antes de todos os eventos aplicáveis.

**Questões não resolvidas**  
As FAQs não cobrem todas as alterações de alvo, permissão, Power, efeito e remoção de objetos em cada etapa (`Q-001`, `Q-007`).

**Cobertura normativa:** `PARTIAL`.

### FCL-023 — Blocker, Counter e modificações do Counter Step

**Descrição do comportamento**  
Representar interações específicas da etapa Block e Counter, incluindo o uso de efeitos Blocker, eventos On Block e modificadores de Counter.

**Significado semântico**  
Blocker é uma capacidade de substituição do alvo dentro de uma etapa definida da batalha. Counter é uma família de ações disponíveis ao defensor durante Counter Step. Modificadores de Counter alteram o valor aplicado ao Leader/Character para a batalha e possuem regra de combinação própria.

**Fundamento oficial**  
CR `7-1-2`, `7-1-3`, `10-1-4`, `10-2-4`, `10-2-15`, `2-10-1` a `2-10-4`.

**Inventário relacionado**  
`R-012`, `R-017`, `R-028`, `R-030`; `T-082` a `T-084`, `T-123`, `T-124`, `T-168`, `T-170`, `T-171`, `T-178`; FAQs `ST31-003`, `OP17-040`, `OP17-063/118`, Promotion Cards `P-001/P-004`.

**Informações que precisam ser representadas**

- carta com Blocker e carta que está sendo atacada;
- condição e custo do Blocker;
- evento de ativação e efeito On Block;
- alvo original e alvo substituto;
- cartas Counter usadas;
- carta que recebe o aumento de Power;
- valor de Counter aplicável e duração;
- restrições de quantidade e de janela.

**Responsabilidade do motor**  
Controlar Block Step/Counter Step, limite de um Blocker por batalha, legalidade de ativação, pagamento, consumo de Counter da mão, duração dos modificadores e regra de aplicação de Counter.

**Responsabilidade do programa da carta**  
Declarar condições especiais de Blocker, efeitos On Block, efeitos de Counter ou alterações de valores de Counter.

**Restrições e comportamentos especiais**

- Blocker só pode ser ativado uma vez durante aquela batalha.
- Blocker rested ou a própria carta já atacada não pode ser usada conforme os esclarecimentos citados no inventário.
- Counter pode ser usado depois de Blocker.
- Event `[Counter]` possui janela específica; efeitos não podem ativá-lo sem autorização textual correspondente.
- Se vários efeitos similares de Counter incidirem na mesma carta, o FAQ OP17-063/118 determina a aplicação somente do maior valor no caso respondido; não somar automaticamente.
- ST31-003 ilustra que uma condição agregada de DON!! dada pode ser verificada depois de efeitos de ataque.

**Questões não resolvidas**  
A regra de “modificadores similares” deve permanecer delimitada ao caso oficial; a composição de Counter com textos não similares requer revisão individual. Outras interações de Blocker/Unblockable e condições agregadas permanecem parciais.

**Cobertura normativa:** `PARTIAL`.

### FCL-024 — Dano, Life, Trigger e substituição de dano

**Descrição do comportamento**  
Representar efeitos que causam, modificam ou substituem dano e respeitar o procedimento oficial de processamento de dano ao Leader.

**Significado semântico**  
Causar dano é diferente de mover uma carta de Life por efeito. O motor processa cada unidade de dano de acordo com a regra, pode oferecer Trigger e retoma o processamento no ponto correto. O programa da carta declara a quantidade ou substituição de dano, não a mecânica inteira da Life Area.

**Fundamento oficial**  
CR `1-2`, `4-6`, `7-1-4`, `10-1-2`, `10-1-3`, `10-1-5`, `8-6-2` e `8-6-2-1`.

**Inventário relacionado**  
`R-001`, `R-007`, `R-012`, `R-015`, `R-016`, `R-017`, `R-024`, `R-027`; `T-001` a `T-007`, `T-044` a `T-047`, `T-088` a `T-091`, `T-114`, `T-115`, `T-122`, `T-125`, `T-149` a `T-154`, `T-164` a `T-167`; `Q-007`, `Q-013`; FAQ `EB05-052` e FAQ geral sobre Double Attack.

**Informações que precisam ser representadas**

- origem e natureza do dano (ataque ou efeito);
- jogador/Leader atingido;
- quantidade de dano;
- se o dano está sujeito a Banish, Double Attack ou substituição;
- evento de dano que pode ser substituído;
- condição, custo e alternativa de substituição;
- ligação com a sequência de dano múltiplo;
- timing para avaliar derrota.

**Responsabilidade do motor**  
Executar o procedimento de dano; remover cartas de Life uma por vez; decidir a janela de Trigger; permitir a interrupção temporária pelo Trigger; retomar o dano; aplicar Banish/Double Attack; executar `rule processing` no momento oficial.

**Responsabilidade do programa da carta**  
Declarar efeitos que causam dano, alteram quantidade/destino do dano ou substituem a situação de receber dano.

**Restrições e comportamentos especiais**

- Mover Life para mão ou Trash por um efeito comum não é, por si só, dano e não ativa Trigger.
- Trigger só é oferecido quando o procedimento de dano adiciona a carta da Life à mão, nas condições do CR.
- Banish manda a carta de Life para Trash em vez de mão e não ativa Trigger.
- Double Attack determina dano múltiplo durante um ataque; Trigger do primeiro dano resolve antes do segundo processamento.
- Se o jogador tem zero Life, a regra de batalha estabelece um momento específico para determinar a vitória; o FAQ EB05-052 registra uma substituição de dano capaz de impedir dano/derrota no cenário perguntado. Não resolver essa tensão por uma ordem inventada.
- A carta de Life que ativa Trigger não pertence a nenhuma área durante a resolução do Trigger.

**Questões não resolvidas**  
A ordem completa entre múltiplas substituições de dano, Double Attack, Trigger, zero Life e derrota continua pendente (`Q-007`, `Q-013`). O resultado do FAQ EB05-052 deve continuar identificado como caso específico até que fontes adicionais delimitem sua generalização.

**Cobertura normativa:** `PARTIAL`.

### FCL-025 — K.O. e eventos de remoção

**Descrição do comportamento**  
Preservar a semântica específica de K.O. e seu evento associado, distinguindo-o de outras movimentações para Trash.

**Significado semântico**  
K.O. é uma forma específica de remoção de Character, causada por derrota em batalha ou por efeito. Uma instrução de K.O. move Character da Character Area para a Trash de seu proprietário. Outras formas de enviar à Trash não geram K.O. nem satisfazem automaticamente On K.O.

**Fundamento oficial**  
CR `7-1-4-1-2`, `10-2-1-1` a `10-2-1-3`, `10-2-17`.

**Inventário relacionado**  
`R-005`, `R-012`, `R-017`, `R-018`, `R-023`; `T-029`, `T-030`, `T-032`, `T-126`, `T-133` a `T-135`, `T-154`, `T-159`, `T-164` a `T-167`.

**Informações que precisam ser representadas**

- se a ação é K.O. ou movimentação comum;
- Character afetado;
- causa (batalha ou efeito), quando relevante;
- jogador que causa/ordena a ação;
- proprietário da carta;
- evento On K.O. gerado;
- timing da ativação e localização da carta na resolução.

**Responsabilidade do motor**  
Aplicar proteções contra K.O.; executar a movimentação para Trash; gerar evento de K.O. apenas quando a regra reconhece K.O.; controlar o ciclo especial de On K.O.

**Responsabilidade do programa da carta**  
Declarar instrução de K.O. ou reação a On K.O., além das condições/custos próprios.

**Restrições e comportamentos especiais**

- Trash por descarte, limite de Character ou outro método não é automaticamente K.O.
- On K.O. ativa no campo; depois a carta vai para Trash e o efeito resolve na Trash.
- Efeitos de proteção contra K.O. não protegem contra toda movimentação para Trash.
- A área de destino e o timing de ativação são importantes para eventos gerados por movimentação.

**Questões não resolvidas**  
Combinações complexas de K.O. simultâneo com várias substituições ainda dependem das lacunas de `Q-003` e `Q-004`.

**Cobertura normativa:** `HIGH` para a distinção K.O./Trash e a sequência básica de On K.O.; `PARTIAL` para interações coletivas. Resultado global: `PARTIAL`.

### FCL-026 — Validade, invalidação e proibições

**Descrição do comportamento**  
Representar efeitos que tornam outra habilidade, uma parte de habilidade ou uma ação válida/inválida, bem como restrições que proíbem determinadas ações.

**Significado semântico**  
A validade de uma habilidade é distinta da condição de ativação e da possibilidade material de executar uma ação. Um efeito pode invalidar outro sob condições específicas; uma proibição pode impedir uma ação mesmo quando outro efeito ordena executá-la.

**Fundamento oficial**  
CR `1-3-3`, `8-2-1` a `8-2-4`, `8-4-6`.

**Inventário relacionado**  
`R-002`, `R-013`, `R-014`, `R-029`; `T-011`, `T-099`, `T-100`; lacunas correlatas `Q-006` e `Q-012`.

**Informações que precisam ser representadas**

- efeito/parte de efeito cuja validade é afetada;
- condição de invalidação ou validade;
- escopo (habilidade inteira, parte, carta, jogador ou ação);
- duração;
- se a invalidação afeta escolhas e custos;
- relação com efeitos já ativados/resolvidos;
- proibição de ação e objetos abrangidos.

**Responsabilidade do motor**  
Impedir que efeitos inválidos ocorram; não permitir escolha ou pagamento de custo associados à parte inválida; dar precedência à proibição ativa; preservar efeitos já resolvidos e efeitos novos recebidos por carta segundo o CR.

**Responsabilidade do programa da carta**  
Declarar precisamente qual efeito/parte/ação é validado, invalidado ou proibido e sob quais condições.

**Restrições e comportamentos especiais**

- Efeito inválido não é equivalente a carta sem efeito base.
- Se uma parte é inválida, sua escolha e seu custo não são executados.
- Efeitos automáticos/ativáveis já ativados e resolvidos não são retroativamente invalidados.
- Novo efeito obtido por carta cujo outro efeito foi invalidado não é automaticamente invalidado.
- Proibição ativa prevalece sobre uma instrução que exija a ação proibida.

**Questões não resolvidas**  
O inventário ainda não reúne um catálogo de todas as redações de negação, proibição e invalidação parcial; a cobertura de casos combinados é limitada.

**Cobertura normativa:** `PARTIAL`.

### FCL-027 — Vitória, derrota e encerramento provocado por efeito

**Descrição do comportamento**  
Representar efeitos que determinam diretamente que um jogador vença ou perca, mantendo separados os procedimentos gerais de derrota e concessão.

**Significado semântico**  
Um efeito pode determinar diretamente vitória/derrota durante sua resolução. Isso é diferente de satisfazer uma condição geral de derrota que será julgada no próximo processamento de regra. Concessão não pode ser criada ou substituída por efeito.

**Fundamento oficial**  
CR `1-2-1` a `1-2-5`, `7-1-4-1-1-1`, `9-2-1`.

**Inventário relacionado**  
`R-001`, `R-012`, `R-016`; `T-001` a `T-007`, `T-117` a `T-119`; `Q-007`, `Q-013`.

**Informações que precisam ser representadas**

- jogador afetado;
- se o efeito determina vitória/derrota direta;
- ou se apenas cria/consulta uma condição de derrota;
- momento em que a condição é avaliada;
- relação com substituição de dano e processamento de regra.

**Responsabilidade do motor**  
Encerrar a partida; aplicar condições gerais de derrota no momento oficial; avaliar derrota simultânea; rejeitar efeitos que tentem forçar/substituir concessão.

**Responsabilidade do programa da carta**  
Declarar somente o resultado de vitória/derrota expressamente determinado pelo texto da carta, quando aplicável.

**Restrições e comportamentos especiais**

- Zero Life, isoladamente, não é condição suficiente de derrota.
- Zero cartas no Deck é condição de derrota julgada em `rule processing`.
- Se ambos satisfazem condição de derrota quando o processamento começa, ambos perdem.
- Efeito que declara vitória/derrota direta resolve durante a própria resolução.
- Concessão encerra imediatamente e não pode ser imposta nem substituída.

**Questões não resolvidas**  
A interação entre dano, substituições, Trigger e derrota com zero Life continua pendente nos casos `Q-007` e `Q-013`.

**Cobertura normativa:** `HIGH` para as distinções gerais; `PARTIAL` para os casos compostos com dano/substituição.

### FCL-028 — Efeitos de construção de deck (escopo separado)

**Descrição do comportamento**  
Registrar a existência de efeitos de carta que modificam regras de construção de deck, embora não pertençam ao processamento normal de uma partida iniciada.

**Significado semântico**  
O CR trata determinados efeitos de construção de deck como permanentes que substituem regras gerais de legalidade durante a construção. Esse domínio é temporalmente anterior à partida e não deve ser confundido com ações executadas pela VM durante o gameplay.

**Fundamento oficial**  
CR `5-1-2-4` a `5-1-2-4-2`.

**Inventário relacionado**  
`R-009`; `T-054` a `T-061`; `Q-009`.

**Informações que precisariam ser representadas caso o domínio seja incluído**

- regra de construção afetada;
- categoria/quantidade/cor/limite alterado;
- escopo de validade durante construção;
- condição e carta que concede a exceção.

**Responsabilidade do motor**  
O validador de deck aplicaria as regras de construção e as exceções válidas antes da partida. Esse procedimento não deve ser executado como efeito comum da VM durante uma batalha.

**Responsabilidade do programa da carta**  
Se o projeto decidir suportar esse domínio, a carta declarará a exceção de construção específica.

**Restrições e comportamentos especiais**

- Não transportar a lista de banimentos para a linguagem de efeitos.
- Raridade, ilustração, idioma e metadados de impressão não constituem comportamento de gameplay.
- Não incluir essa capacidade no núcleo de execução de partida apenas porque existe uma regra de construção modificável.

**Questões não resolvidas**  
As cartas que modificam construção de deck ainda não foram inventariadas integralmente (`Q-009`). A decisão de suportar esse domínio pertence a uma etapa posterior de delimitação funcional.

**Cobertura normativa:** `LIMITED` para o mecanismo específico descrito no CR; catálogo de cartas afetadas não auditado.

### FCL-029 — Loops e encerramento de processamento repetitivo

**Descrição do comportamento**  
Identificar a necessidade de reconhecer loops infinitos resultantes de sequências de ações e aplicar o procedimento oficial de encerramento, sem transformar a linguagem em uma linguagem de programação geral.

**Significado semântico**  
A regra de loops infinitos define como a partida continua ou termina quando um processamento pode ou deve ocorrer infinitamente. Isso não implica que o programa de uma carta deva dispor de laços arbitrários. Repetições explícitas e finitas podem ser representadas; a detecção e resolução de um ciclo infinito pertencem ao motor.

**Fundamento oficial**  
CR `11-1-1-1` a `11-1-1-3`.

**Inventário relacionado**  
`R-019`; `T-136` a `T-138`; `Q-010`.

**Informações que precisam ser representadas ou observadas**

- sequência de ações que forma um ciclo;
- estado relevante antes/depois da repetição;
- jogadores que podem interromper o ciclo;
- momento em que cada jogador pode parar;
- quantidade de repetições declarada por cada jogador quando exigida.

**Responsabilidade do motor**  
Reconhecer e administrar o loop segundo o CR, determinar se cada jogador pode interrompê-lo e aplicar empate ou limite de repetições conforme o caso.

**Responsabilidade do programa da carta**  
Declarar operações e repetições finitas que o texto efetivamente exige. Não definir uma regra alternativa de loop.

**Restrições e comportamentos especiais**

- Se nenhum jogador puder parar, a partida termina empatada.
- Se somente um puder parar, esse jogador declara quantas repetições executar.
- Se ambos puderem parar, o jogador do turno escolhe primeiro e o menor número escolhido determina a repetição final nos termos do CR.
- Não presumir que toda repetição textual pode ser substituída por um laço ilimitado.

**Questões não resolvidas**  
Não há inventário completo de cartas que formam loops concretos. A auditoria deve buscar esses casos sem presumir que a linguagem necessite de controle de fluxo irrestrito (`Q-010`).

**Cobertura normativa:** `PARTIAL` para a política de regras; `LIMITED` para a necessidade concreta da linguagem.

### FCL-030 — Limite de uso `[Once Per Turn]`

**Descrição do comportamento**  
Representar a restrição segundo a qual uma habilidade marcada `[Once Per Turn]` pode ser ativada e resolvida somente uma vez durante aquele turno, mantendo o limite associado à instância da carta.

**Significado semântico**  
`Once Per Turn` não é apenas uma condição `Your Turn` e não é uma duração de efeito. É um limite de uso que registra se aquela habilidade já consumiu sua oportunidade no turno atual. O limite depende da identidade da carta/instância e do estado do processo de ativação/pagamento.

**Fundamento oficial**  
CR `10-2-13-1` a `10-2-13-5`, em conjunto com `3-1-6` e `8-3-1-3-1`.

**Inventário relacionado**  
`R-018`, `R-014`, `R-027`; `T-129` a `T-132`; FAQs `EB05-061` e `OP17-015/095` conforme o escopo registrado no inventário.

**Informações que precisam ser representadas**

- habilidade sujeita ao limite;
- instância de carta à qual o limite pertence;
- turno atual;
- estado de uso (disponível, ativado/consumido ou resolvido, conforme a etapa normativa);
- se o processo de pagamento tornou-se impossível depois de a habilidade ter sido ativada;
- evento de mudança de área que possa gerar nova instância.

**Responsabilidade do motor**  
Manter e consultar o limite por carta/instância/turno; impedir nova ativação ou novo pagamento quando o limite tiver sido consumido; reconhecer quando sair e retornar ao campo torna a carta uma instância diferente; tratar a impossibilidade parcial de pagamento conforme `10-2-13-5`.

**Responsabilidade do programa da carta**  
Declarar que a habilidade está sujeita a `[Once Per Turn]`. O programa não administra diretamente o contador nem define quando ele é reiniciado.

**Restrições e comportamentos especiais**

- O limite é por carta: várias cópias podem usar a habilidade uma vez cada.
- Depois da resolução, a habilidade não pode ser ativada novamente no mesmo turno, mesmo que as condições se satisfaçam outra vez; seu custo também não pode ser pago novamente.
- Se a carta sai da área e retorna ao campo, é tratada como carta diferente e pode usar novamente a habilidade.
- Se o efeito foi ativado e o pagamento tornou-se impossível no meio do processo, a habilidade não pode ser ativada novamente naquele turno, mesmo que o texto principal não tenha resolvido.
- `[Once Per Turn]` não deve ser aplicado automaticamente a qualquer outra habilidade impressa na mesma carta se o texto não a abranger.

**Questões não resolvidas**  
A regra geral está expressa no CR. O que permanece pendente é a classificação integral de redações concretas e a validação das interações com efeitos que mudam de área, mudam de controle ou alteram o próprio efeito (`Q-002`, `Q-012`).

**Cobertura normativa:** `HIGH` para a regra geral; classificação do catálogo continua pendente.

## 5. Responsabilidades gerais do motor que não devem virar instruções de carta

As regras abaixo precisam ser executadas pelo motor, mas não precisam ser reexpressas em cada programa. A linguagem pode declarar uma operação que invoque um procedimento; não deve reproduzir o procedimento completo.

| Domínio | Responsabilidade do motor | Relação com programas de cartas |
|---|---|---|
| Fluxo da partida | Número de jogadores, fases, ordem do turno e janelas de ação. | Programas declaram timings e efeitos; não controlam o fluxo global. |
| Legalidade de ações | Determinar se ataque, Blocker, Counter, jogo de carta ou outra ação é permitido naquele momento. | Programas podem conceder exceções ou restrições expressas. |
| Zonas e informação | Controlar áreas, visibilidade, ordem, cartas ocultas e identidade de instâncias. | Programas declaram consultas/movimentações. |
| Procedimentos de dano | Processar Life, Trigger, Banish, Double Attack e interrupções. | Programas causam ou modificam dano, mas não implementam o procedimento de dano. |
| Rule processing | Executar automaticamente processos normativos, inclusive julgamento de derrota. | Programas podem criar o evento/estado que inicia o processamento. |
| Prioridade/ordem entre efeitos | Processar efeitos simultâneos, substituições e efeitos permanentes conforme CR. | Programas declaram os efeitos e condições relevantes. |
| Durações globais | Abrir/fechar turno e batalha, expirar efeitos e remover modificações no timing correto. | Programas declaram a duração pretendida. |
| Proibições e impossibilidade | Impedir ações proibidas e tratar ações impossíveis conforme o CR. | Programas não redefinem a regra geral de impossibilidade. |
| Vitória/derrota | Julgar condições gerais, encerrar a partida e processar concessão. | Programas podem declarar resultados diretos quando o texto permitir. |
| Limites de zona | Aplicar limite de Characters/Stages e procedimentos especiais de substituição. | O programa não imita o procedimento do sexto Character ou substituição de Stage. |
| Regras de keyword | Executar semântica oficial de Rush, Blocker, Trigger etc. | Programas declaram/grantem/retiram keywords ou seus efeitos particulares. |
| Legalidade de deck | Validar deck e exceções de construção antes da partida. | Domínio separado do núcleo de efeitos de gameplay. |

## 6. Limites negativos da linguagem

Com a cobertura documental atual, a linguagem **não deve** assumir as seguintes capacidades sem investigação adicional:

1. **Controle de fluxo irrestrito.** A existência de `if`, `then` e repetições não justifica uma linguagem Turing-completa ou laços arbitrários.
2. **Seleção universal.** Não existe uma regra única que determine para todas as cartas quem escolhe, quem move e de quem é a carta.
3. **Targeting com revalidação universal.** Não presumir que um alvo escolhido seja sempre revalidado, substituído ou descartado se mudar de zona.
4. **Snapshot universal.** Não congelar automaticamente todas as condições/características na ativação.
5. **Reavaliação contínua universal.** Não recalcular toda condição a cada mudança de estado sem que a regra/texto peça isso.
6. **Substituição coletiva genérica.** Não presumir que um pagamento salva qualquer número de cartas ou que cada carta removida exige um pagamento independente.
7. **Equivalência de movimentações.** `Trash`, `K.O.`, `Remove`, dano e movimento de Life não são sinônimos.
8. **Equivalência de jogadores.** `player`, `owner`, jogador que ativa, jogador que escolhe e jogador que executa não são um único papel.
9. **Cancelamento automático por movimento da origem.** Um efeito pendente, ativado, resolvido ou Trigger possui regras diferentes.
10. **Regra única para danos múltiplos.** Double Attack, Trigger e substituições precisam preservar seus timings próprios.
11. **Características apenas impressas.** Valores efetivos podem ser derivados de diversos efeitos e condições.
12. **Every effect is all-or-nothing.** A impossibilidade de uma ação não apaga necessariamente as demais.
13. **FAQ como regra geral.** Uma resposta de carta só pode ser generalizada quando outra fonte normativa sustentar essa generalização.
14. **Toda a Comprehensive Rules como linguagem.** A linguagem descreve efeitos. O motor conserva as regras globais.

## 7. Mapa de dependências semânticas

As capacidades não são independentes. A especificação abstrata posterior deve preservar, pelo menos, estas relações:

| Capacidade | Depende de |
|---|---|
| `FCL-001` Efeitos | `FCL-002`, `FCL-003`, `FCL-004`, `FCL-018`, `FCL-021` |
| `FCL-002` Timing | `FCL-008`, `FCL-019`, contexto de fase/batalha do motor |
| `FCL-003` Condições | `FCL-007`, `FCL-009`, `FCL-012`, `FCL-016`, avaliação temporal |
| `FCL-004` Custos | `FCL-005`, `FCL-006`, `FCL-007`, `FCL-012`, `FCL-017` |
| `FCL-005` Opcionalidade | `FCL-004`, `FCL-006`, `FCL-017`, `FCL-018` |
| `FCL-006` Escolhas | `FCL-007`, `FCL-009`, `FCL-010`, `FCL-011` |
| `FCL-008` Origem | `FCL-002`, `FCL-019`, `FCL-021`, `FCL-025` |
| `FCL-009` Consultas | `FCL-007`, `FCL-011`, `FCL-014`, `FCL-016` |
| `FCL-010` Movimentação | `FCL-007`, `FCL-008`, `FCL-011`, `FCL-019`, `FCL-025` |
| `FCL-012` DON!! | `FCL-004`, `FCL-006`, `FCL-009`, `FCL-014` |
| `FCL-014` Características | `FCL-009`, `FCL-016`, `FCL-021`, `FCL-023` |
| `FCL-016` Contínuos | `FCL-003`, `FCL-009`, `FCL-014`, `FCL-021` |
| `FCL-017` Substituições | `FCL-004`, `FCL-005`, `FCL-006`, `FCL-018`, `FCL-019`, `FCL-024`, `FCL-025` |
| `FCL-018` Sequência | `FCL-003`, `FCL-004`, `FCL-005`, `FCL-006`, `FCL-017`, `FCL-019` |
| `FCL-019` Eventos | `FCL-002`, `FCL-008`, `FCL-017`, `FCL-020`, `FCL-025` |
| `FCL-020` Multiplicidade | `FCL-005`, `FCL-006`, `FCL-010`, `FCL-018`, `FCL-019`, `FCL-024` |
| `FCL-021` Duração | `FCL-008`, `FCL-014`, `FCL-016`, `FCL-019`, contexto de turno/batalha do motor |
| `FCL-022` Batalha | `FCL-002`, `FCL-014`, `FCL-015`, `FCL-023`, `FCL-024` |
| `FCL-023` Blocker/Counter | `FCL-002`, `FCL-006`, `FCL-014`, `FCL-015`, `FCL-022` |
| `FCL-024` Dano/Life | `FCL-006`, `FCL-017`, `FCL-019`, `FCL-025`, `FCL-027` |
| `FCL-025` K.O. | `FCL-008`, `FCL-010`, `FCL-017`, `FCL-019` |

| `FCL-030` Once Per Turn | `FCL-001`, `FCL-002`, `FCL-004`, `FCL-008`, `FCL-021` |

O mapa indica dependências conceituais, não uma ordem de implementação.

## 8. Matriz resumida de cobertura

| ID | Capacidade | Cobertura normativa | Principal lacuna |
|---|---|---|---|
| `FCL-001` | Declaração/classificação de efeitos | `HIGH` | Classificação integral do catálogo de cartas/erratas |
| `FCL-002` | Timing | `PARTIAL` | Interações entre janelas e FAQs não auditados |
| `FCL-003` | Condições | `PARTIAL` | Momento de avaliação, `if you do`, áreas secretas |
| `FCL-004` | Custos | `PARTIAL` | Repertório completo e custos substituídos |
| `FCL-005` | Opcionalidade | `PARTIAL` | Erratas e textos compostos |
| `FCL-006` | Escolhas/alvos | `PARTIAL` | Papéis e seleções ocultas |
| `FCL-007` | Papéis de jogadores | `PARTIAL` | Combinações completas de escolher/executar/possuir |
| `FCL-008` | Carta de origem | `PARTIAL` | Mais casos de mudança de área e efeitos pendentes |
| `FCL-009` | Consultas/filtros | `PARTIAL` | Valores exatos/base/efetivos e zonas secretas |
| `FCL-010` | Movimentação de zonas | `PARTIAL` | Movimentos simultâneos/secretos e propriedade |
| `FCL-011` | Informação oculta | `PARTIAL` | Efeitos que alcançam áreas secretas |
| `FCL-012` | DON!! | `PARTIAL` | Condições agregadas e interações de pagamento |
| `FCL-013` | Ações elementares | `PARTIAL` | Inventário completo de operações recorrentes |
| `FCL-014` | Características | `PARTIAL` | Camadas, snapshots e origem de keywords |
| `FCL-015` | Keywords | `PARTIAL` | Concessão/negação dinâmica; núcleo de keywords é `HIGH` |
| `FCL-016` | Efeitos permanentes/contínuos | `PARTIAL` | Objetos futuros, snapshots e zonas secretas |
| `FCL-017` | Replacement Effects | `PARTIAL` | Concorrência, encadeamento e escopo coletivo |
| `FCL-018` | Sequência/condicionais | `PARTIAL` | `if you do` e composição de cláusulas |
| `FCL-019` | Eventos/gatilhos | `PARTIAL` | Fila completa em danos e efeitos simultâneos |
| `FCL-020` | Multiplicidade | `PARTIAL` | Loops e ações coletivas versus individuais |
| `FCL-021` | Duração/escopo | `PARTIAL` | Regras de snapshot/escopo temporal |
| `FCL-022` | Ataques/batalhas | `PARTIAL` | Redirecionamentos e alterações por etapa |
| `FCL-023` | Blocker/Counter | `PARTIAL` | Composição de modificadores e condições |
| `FCL-024` | Dano/Life/Trigger | `PARTIAL` | Zero Life com substituições encadeadas |
| `FCL-025` | K.O. | `PARTIAL` | K.O. simultâneo com Replacement Effects |
| `FCL-026` | Validade/proibição | `PARTIAL` | Catálogo de negação/invalidação |
| `FCL-027` | Vitória/derrota | `HIGH` | Interação composta com dano/substituição |
| `FCL-028` | Construção de deck | `LIMITED` | Cartas que modificam construção não inventariadas |
| `FCL-029` | Loops infinitos | `PARTIAL` | Exemplos concretos de cartas não inventariados |
| `FCL-030` | Limite `[Once Per Turn]` | `HIGH` | Cobertura de interações de uso por instância/retorno ainda deve ser testada no catálogo |

## 9. Questões normativas herdadas e lacunas que afetam a linguagem

As questões do inventário v0.3 continuam válidas. As seguintes têm impacto direto sobre a semântica da linguagem:

| Questão | Impacto na linguagem | Tratamento nesta versão |
|---|---|---|
| `Q-001` FAQs antigos não auditados | Podem acrescentar timings, condições, custos, ações ou exceções. | Não presumir ausência de regras; manter cobertura parcial. |
| `Q-002` Reconciliação integral de erratas | Pode alterar semântica de cartas concretas: condição, custo, quantidade, ator ou opcionalidade. | O texto corrigido é normativo; catálogo continua pendente. |
| `Q-003` Substituições concorrentes/encadeadas | Afeta o modo de representar e resolver Replacement Effects. | Semântica geral CR preservada; combinações específicas abertas. |
| `Q-004` Escopo coletivo de substituições | Afeta alvos, quantidade e custo de substituições. | Não inferir pagamento por objeto nem abrangência automática. |
| `Q-005` Papéis de escolha/execução | Exige distinguir quem ativa, escolhe, paga, move e é proprietário. | Papéis separados; cobertura incompleta. |
| `Q-006` Momento de avaliação de condições | Afeta condições antes/depois de custos e ações precedentes. | Nenhum snapshot/recalculo universal é definido. |
| `Q-007` Dano/Double Attack/Trigger/substituição | Afeta sequência, interrupção e derrota. | Regras gerais separadas dos casos de FAQ. |
| `Q-008` Efeitos em áreas secretas | Afeta filtros, características e consulta a objetos ocultos. | Respeitar exceções explícitas; não generalizar. |
| `Q-009` Construção de deck | Afeta possível extensão pré-jogo da linguagem. | Capacidade isolada e fora do núcleo de partida. |
| `Q-010` Loops de cartas | Afeta necessidade de repetição e terminação. | Motor trata regra de loop; não autorizar laços irrestritos. |
| `Q-011` Diferenças regionais/idiomas | Pode alterar o texto efetivo das cartas. | Resolver somente em caso de conflito concreto. |
| `Q-012` Escopo temporal de modificadores | Afeta alvo, duração, aplicação a cartas futuras e base/valor efetivo. | Não definir regra geral; exigir rastreabilidade por caso. |
| `Q-013` Derrota em zero Life com substituições | Afeta dano, substituição e encerramento. | Manter caso EB05-052 delimitado; investigar antes de generalizar. |

## 10. Decisões semânticas estabelecidas nesta versão

As afirmações abaixo são decisões de delimitação desta especificação, respaldadas pelas regras já identificadas:

1. A linguagem representa **efeitos de cartas**, não a Comprehensive Rules inteira.
2. As categorias `auto`, `activate`, `permanent` e `replacement` permanecem distintas de `one-shot` e `continuous`.
3. Timing, condição, custo, escolha e resolução são conceitos separados.
4. O programa declara a operação; o motor aplica o procedimento oficial da operação.
5. O programa deve preservar a distinção entre jogador que ativa, jogador que escolhe, jogador que paga, jogador que executa e proprietário do objeto.
6. A referência à origem precisa sobreviver semanticamente durante as etapas de ativação/resolução, respeitando mudanças de área e exceções.
7. Consultas a características devem diferenciar base, efetivo, valor exato, limite e conjunto agregado.
8. A movimentação entre áreas não implica automaticamente K.O., dano ou Trigger.
9. DON!! dado, DON!! na Cost Area e DON!! no DON!! Deck são estados semânticos diferentes.
10. Efeitos contínuos e Replacement Effects exigem tratamento próprio; não podem ser simulados apenas como sequências de alterações pontuais.
11. `if`, `then`, opcionalidade, impossibilidade e multiplicidade precisam conservar suas consequências específicas.
12. Batalha, dano, Life, Trigger e `rule processing` permanecem procedimentos do motor.
13. Uma carta que contenha comportamento ainda não representável deve permanecer explicitamente não suportada/bloqueada; não deve receber aproximação silenciosa.
14. A linguagem não deve ganhar construções gerais de programação sem uma necessidade normativa demonstrável.
15. `[Once Per Turn]` é um limite semântico com ciclo de vida próprio; não pode ser reduzido a uma simples condição de turno nem a uma duração comum.

## 11. Critério para avançar ao modelo abstrato

A semântica poderá avançar para o modelo abstrato quando:

1. cada capacidade essencial tiver significado delimitado e responsabilidade atribuída;
2. as distinções normativas listadas na seção 3.5 estiverem preservadas;
3. não houver contradição conhecida entre capacidades;
4. as dependências entre escolha, custo, condições, eventos, substituição, duração e movimentação estiverem suficientemente explícitas;
5. lacunas relevantes estiverem associadas a `Q-xxx` e a uma classificação de cobertura;
6. o catálogo de capacidades estiver confrontado com uma amostra representativa de textos reais de cartas, sem usar exemplos como fonte de regras gerais;
7. o documento identificar explicitamente o que a primeira versão da linguagem não pretende suportar.

Esse critério não exige concluir toda a auditoria de FAQs/erratas antes de avançar. Exige que as lacunas sejam conhecidas, rastreáveis e não mascaradas.

## 12. Fora do escopo deste documento

Este documento não determina:

- gramática ou schema TOML;
- nomes finais de campos, tags ou construções;
- opcodes, instruction set ou bytecode;
- representação binária ou compilação;
- tipos concretos e sistema de tipagem;
- implementação Kotlin, módulos, pacotes ou arquitetura da VM;
- ordem de implementação, cronograma ou plano de testes;
- tradução de qualquer carta individual.

Esses assuntos dependem da consolidação da semântica e do modelo abstrato.

## 13. Histórico

| Versão | Data | Alteração |
|---|---|---|
| `0.1` | 08/11/2026 | Primeira especificação semântica: catálogo provisório de capacidades, separação entre programa e motor, rastreabilidade para `GAME_RULES_INVENTORY_v0.3.md`, lacunas e critérios de avanço. |

---

**Status final desta versão:** `DRAFT — SEMANTIC SPECIFICATION STARTED`  
**Próxima etapa conceitual, não iniciada neste documento:** consolidar e revisar o modelo abstrato a partir das capacidades semânticas, mantendo as lacunas normativas visíveis.
