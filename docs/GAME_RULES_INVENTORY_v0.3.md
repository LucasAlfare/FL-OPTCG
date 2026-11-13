# FL-OPTCG — Inventário das regras oficiais do jogo

**Versão:** 0.3 — auditoria complementar normativa em andamento  
**Data de referência:** 7 de novembro de 2026  
**Base principal:** *ONE PIECE CARD GAME Comprehensive Rules*, v1.2.1, atualizado em 28/08/2026  
**Status da Fase 1:** `IN_PROGRESS` — o inventário foi corrigido e ampliado com auditoria documental adicional; FAQs históricos e reconciliação integral de erratas continuam pendentes.

> Este documento organiza conhecimento normativo para orientar a reconstrução do simulador. Não é uma transcrição integral das regras, uma especificação da linguagem de efeitos, uma especificação de VM ou uma arquitetura de software. A classificação de uma regra indica responsabilidade conceitual; não determina classes, instruções ou opcodes.

---

## 1. Objetivo e limites

O simulador será reconstruído sem tratar a implementação anterior como referência de comportamento. O fato de ela ter compilado não comprova que represente corretamente as regras do jogo.

O objetivo desta fase é documentar, com rastreabilidade:

1. o comportamento geral do jogo;
2. as condições e os momentos em que cada comportamento acontece;
3. a ordem de processamento e as prioridades aplicáveis;
4. as exceções e interações que alteram o resultado;
5. quais comportamentos são próprios do motor e quais também precisam ser acionáveis por efeitos de cartas;
6. cenários concretos que possam ser usados posteriormente em testes de conformidade.

**Fora do trabalho desta versão:** desenho da linguagem TOML das cartas, sintaxe, tipagem, catálogo de instruções/opcodes, VM, arquitetura de módulos, implementação Kotlin e reaproveitamento de classes antigas.

### 1.1 Classificações de responsabilidade

Cada registro recebe uma destas classificações:

- `ENGINE_RULE`: procedimento geral do jogo que o motor deve executar, independentemente de uma carta específica.
- `CARD_EFFECT_CAPABILITY`: comportamento cuja representação é necessária para expressar uma habilidade de carta. A classificação não determina como essa representação será feita.
- `BOTH`: regra geral que precisa ser executada pelo motor e também é utilizada ou alterada por efeitos de cartas.
- `OUT_OF_SCOPE`: regra ou procedimento que não pertence ao núcleo de uma partida digital. A exclusão é justificada e não significa que a regra seja inexistente.

Uma mesma área temática pode conter regras de classificações diferentes. A classificação deve ser aplicada ao comportamento identificado, não indiscriminadamente ao capítulo inteiro.

### 1.2 Natureza das afirmações

Os registros distinguem:

- `OFFICIAL_RULE`: afirmação expressamente sustentada por documento oficial.
- `INTERPRETATION`: conclusão obtida ao relacionar uma ou mais regras oficiais. Não deve ser citada como se fosse texto literal.
- `OPEN_QUESTION`: ponto para o qual as fontes examinadas ainda não fornecem uma resposta suficiente.
- `IMPLEMENTATION_NOTE`: consequência ou risco a considerar no simulador. Não é uma regra oficial nem uma decisão arquitetural.

### 1.3 Como ler as referências

As referências usam o identificador da regra do Comprehensive Rules (por exemplo, `8-3-1-3-1`) e a página impressa no documento. Para FAQs, o identificador da carta/pergunta e a página do PDF são indicados quando disponíveis. Os documentos oficiais estão relacionados na seção 2.

A tradução dos comportamentos para português é descritiva. Quando uma nuance depender de uma expressão inglesa do texto oficial — como `instead`, `up to`, `K.O.`, `player` ou `owner` — a expressão original é mantida.

---

## 2. Fontes oficiais consultadas

### 2.1 Fontes normativas gerais

| ID | Documento oficial | Versão/data | Utilização nesta revisão |
|---|---|---|---|
| `SRC-CR` | [Comprehensive Rules](https://en.onepiece-cardgame.com/pdf/rule_comprehensive.pdf?20260828=) | v1.2.1 — 28/08/2026 | Fonte normativa principal. O texto do PDF foi consultado ao longo dos 11 capítulos; as páginas críticas de zonas, fases, batalhas, efeitos, processamento e keywords também foram inspecionadas visualmente. |
| `SRC-GFAQ` | [Q&A General Rules](https://en.onepiece-cardgame.com/pdf/qa_rules.pdf?20250228=) | Atualizado em 28/02/2025 | Esclarecimentos gerais sobre regras e interações. Documento de 10 páginas consultado integralmente em texto. |
| `SRC-RM` | [Official Rule Manual](https://en.onepiece-cardgame.com/rules/) | 23/06/2023 | Consultado como material introdutório/histórico. Não substitui o Comprehensive Rules v1.2.1. |
| `SRC-TRM` | [Tournament Rules Manual](https://en.onepiece-cardgame.com/pdf/tournament_rules_manual.pdf?20260116=) | 16/01/2026 | Consultado para separar procedimentos de torneio das regras de uma partida. Não é tratado como fundamento de mecânica de cartas quando o assunto é exclusivamente organização de evento. |

### 2.2 FAQs específicos de cartas consultados

A [página oficial de FAQs](https://en.onepiece-cardgame.com/rules/faq/) é o índice de referência para localizar os documentos. A atualização indicada abaixo é a data publicada pela Bandai no índice ou no comunicado correspondente; não deve ser inferida do parâmetro de cache da URL.

| ID | Documento oficial | Atualização indicada pela Bandai | Auditoria realizada nesta versão |
|---|---|---:|---|
| `SRC-FAQ-EB05` | [FAQ EB-05](https://en.onepiece-cardgame.com/pdf/qa_eb05.pdf?20261106=) | 06/11/2026 | Leitura textual e análise temática das perguntas relacionadas a custos, condições, escolhas, dano, `instead`, atributos e `Once Per Turn`; cobertura temática, não exaustão de todas as interações possíveis. |
| `SRC-FAQ-OP17` | [FAQ OP-17](https://en.onepiece-cardgame.com/pdf/qa_op17.pdf?20261030=) | 30/10/2026 | Leitura do documento de 10 páginas; perguntas relevantes sobre substituições simultâneas, ordem de timing, custos, escolhas, valores e duração foram extraídas. Auditoria de cobertura temática, não certificação de cada carta do set. |
| `SRC-FAQ-OP16` | [FAQ OP-16](https://en.onepiece-cardgame.com/pdf/qa_op16.pdf?20261030=) | 30/10/2026 | Leitura do documento de 9 páginas; perguntas sobre valores exatos, condições, efeitos parciais, movimento da origem, escolha por proprietário, Counter e DON!! foram extraídas. |
| `SRC-FAQ-ST34` | [FAQ ST-34](https://en.onepiece-cardgame.com/pdf/qa_st-34.pdf?20260821=) | 21/08/2026 | Documento de 1 página examinado integralmente. |
| `SRC-FAQ-ST36` | [FAQ ST-36](https://en.onepiece-cardgame.com/pdf/qa_st-36.pdf?20260717=) | 17/07/2026 | Documento de 1 página examinado integralmente. |
| `SRC-FAQ-ST35` | [FAQ ST-35](https://en.onepiece-cardgame.com/pdf/qa_st-35.pdf?20260717=) | 17/07/2026 | Documento de 1 página examinado integralmente. |
| `SRC-FAQ-ST33` | [FAQ ST-33](https://en.onepiece-cardgame.com/pdf/qa_st-33.pdf?20260717=) | 17/07/2026 | Documento de 1 página examinado integralmente. |
| `SRC-FAQ-ST32` | [FAQ ST-32](https://en.onepiece-cardgame.com/pdf/qa_st-32.pdf?20260717=) | 17/07/2026 | Documento de 1 página examinado integralmente. |
| `SRC-FAQ-ST31` | [FAQ ST-31](https://en.onepiece-cardgame.com/pdf/qa_st-31.pdf?20260717=) | 17/07/2026 | Documento de 1 página examinado integralmente. |
| `SRC-FAQ-OP14-EB04` | [FAQ OP14-EB04](https://en.onepiece-cardgame.com/pdf/qa_op14_eb04.pdf?20260626=) | 26/06/2026 | Leitura do documento de 10 páginas; perguntas relevantes sobre seleção, movimento de área, Trigger, base Power, duração e resolução foram extraídas. |
| `SRC-FAQ-ST30` | [FAQ ST-30 — índice oficial](https://en.onepiece-cardgame.com/rules/faq/) | 29/05/2026 | Documento identificado no índice; conteúdo não auditado nesta versão. |
| `SRC-FAQ-OP15-EB04` | [FAQ OP15-EB04](https://en.onepiece-cardgame.com/pdf/faq_op15-eb04.pdf?20260313=) | 13/03/2026 | Leitura do documento de 15 páginas; perguntas relevantes sobre substituições, escolha, DON!!, deck vazio, condições e duração foram extraídas. |
| `SRC-FAQ-EB03` | [FAQ EB-03](https://en.onepiece-cardgame.com/pdf/qa_eb03.pdf?20260206=) | 06/02/2026 | Leitura do documento de 4 páginas; perguntas relevantes sobre Power negativo, condições, ataque e resolução parcial foram extraídas. |
| `SRC-FAQ-PROMO` | [FAQ Promotion Cards](https://en.onepiece-cardgame.com/pdf/qa_promotion-cards.pdf?20260206=) | 06/02/2026 | Leitura do documento de 6 páginas; perguntas relevantes sobre declaração de ataque, redirecionamento, Blocker, substituição e vitória foram extraídas. |
| `SRC-FAQ-OP13` | [FAQ OP-13 — índice oficial](https://en.onepiece-cardgame.com/rules/faq/) | 06/02/2026 | Documento identificado no índice; conteúdo não auditado nesta versão. |
| `SRC-FAQ-OP12` | [FAQ OP-12 — índice oficial](https://en.onepiece-cardgame.com/rules/faq/) | 06/02/2026 | Documento identificado no índice; conteúdo não auditado nesta versão. |
| `SRC-FAQ-ST29` | [FAQ ST-29 — índice oficial](https://en.onepiece-cardgame.com/rules/faq/) | 09/01/2026 | Documento identificado no índice; conteúdo não auditado nesta versão. |
| `SRC-FAQ-INDEX` | [Listagem oficial de FAQs](https://en.onepiece-cardgame.com/rules/faq/) | Consultada em 07/11/2026 | Confirma também FAQs anteriores, incluindo PRB02, ST22–ST28, OP11, EB02, ST21, OP10, OP07 e sets mais antigos. A presença no índice não é registrada como auditoria de conteúdo. |

**Confirmação em relação ao v0.2:** a data de atualização de EB-05 em **06/11/2026** estava correta. A listagem oficial da Bandai confirma essa data; OP-17 e OP-16 permanecem indicados como atualizados em **30/10/2026**. A data publicada no índice oficial prevalece sobre inferências feitas a partir do parâmetro de cache da URL.

**Limite de cobertura:** esta versão amplia a auditoria de conteúdo, mas não afirma ter auditado integralmente todos os FAQs listados pela Bandai. Os documentos identificados como “não auditados” continuam pendentes; nos PDFs maiores, os achados registrados abaixo são os casos de relevância normativa extraídos nesta passada, e não uma conclusão de que não existam outras perguntas relevantes.

### 2.3 Erratas e atualizações oficiais

| ID | Fonte oficial | Data | Conteúdo verificado |
|---|---|---:|---|
| `SRC-ERRATA-HUB` | [Errata Cards](https://en.onepiece-cardgame.com/rules/errata_card/) | Página exibida como 16/05/2025; contém entradas históricas anteriores | O texto `After` prevalece sobre o original em todos os formatos. Foram conferidos os registros indicados na seção 4; a reconciliação de todo o catálogo continua pendente. |
| `SRC-ERRATA-EB05` | [Errata EB-05](https://en.onepiece-cardgame.com/news/notice_eb05.html) | 23/10/2026 | `EB05-033 Nico Robin`: `O-Robi` → `Nico Robin`; o comunicado inclui versões alt-art. |
| `SRC-ERRATA-OP16` | [Errata OP-16](https://en.onepiece-cardgame.com/news/notice-op16.html) | 29/05/2026 | `OP16-081 Otama`: `If you have a Character...` → `If there is a Character...`; alt-art incluída. |
| `SRC-ERRATA-OP15` | [Errata OP15-EB04](https://en.onepiece-cardgame.com/topics/notice_op15-023.php) | 13/03/2026 | `OP15-023 Arlong`: remove a exigência `rested` do DON!! selecionável; alt-art incluída. |
| `SRC-ERRATA-OP01` | [“Up to” Card Errata — OP-01](https://en.onepiece-cardgame.com/rules/announcements/op01.php) | 11/11/2022 | Comunicado específico com categorias de redação, listas de cartas afetadas e exceções. Não é uma alteração universal de todo texto que contém “1”. |

### 2.4 Hierarquia prática e limites das fontes

A Bandai declara expressamente que o texto `After` das erratas do hub se aplica a todos os formatos e prevalece sobre a redação original. Os comunicados avulsos também dizem que a revisão indicada deve ser tratada como correta. Portanto, uma errata aplicável é obrigatória para determinar o texto vigente da carta.

O Comprehensive Rules estabelece as regras gerais do jogo. Os FAQs oficiais esclarecem situações específicas e a aplicação dessas regras a cartas concretas. Um FAQ de carta não deve ser promovido automaticamente a regra universal para qualquer texto parecido. Quando uma resposta específica parecer tensionar uma regra geral, devem ser preservados: (a) o texto literal da regra geral; (b) o caso exato perguntado no FAQ; e (c) a extensão que a fonte efetivamente autoriza. Se a relação continuar ambígua, registrar `OPEN_QUESTION` em vez de inventar uma precedência.

A ausência de errata no hub central não prova ausência de correção: alguns comunicados são publicados em páginas de notícias/tópicos separadas. A busca por erratas deve combinar o hub, os comunicados oficiais do set e a lista oficial de FAQs, sem usar bases de cartas de terceiros como fonte normativa.

## 3. Inventário por domínio de regra

## 3.1 Número de jogadores, derrota e encerramento

**Registro:** `R-001`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `IMPLEMENTATION_NOTE`  
**Fonte:** `SRC-CR`, §§1-1 e 1-2, pp. 1–2; `SRC-FAQ-EB05`, EB05-052, p. 2.

### OFFICIAL_RULE — Comportamento oficial

- O jogo é definido para dois jogadores. As regras atuais não dão suporte a partidas com três ou mais jogadores (`1-1-1`).
- A partida termina quando um jogador perde; o outro jogador vence (`1-2-1`).
- As duas condições gerais de derrota são:
  - o jogador tem zero Life e seu Leader recebe dano (`1-2-1-1-1`);
  - o jogador tem zero cartas no deck (`1-2-1-1-2`).
- Ao satisfazer uma condição de derrota, a derrota é processada no próximo momento de `rule processing` (`1-2-2`).
- Concessão encerra a partida imediatamente. Não pode ser imposta por efeito nem substituída (`1-2-3` e `1-2-4`).
- Um efeito de carta pode determinar diretamente que um jogador vença ou perca; nesse caso, o resultado ocorre durante a resolução desse efeito (`1-2-5`).
- O julgamento de derrota verifica todas as condições aplicáveis quando o `rule processing` começa (`9-2-1`). Se ambos satisfizerem uma condição nesse momento, ambos perdem (`9-2-1`).

### OFFICIAL_RULE — Condições e timing

A condição “zero Life” não causa derrota por si só. É necessário que o Leader receba dano. A condição “zero cartas no deck” é uma condição por si só quando começa o processamento correspondente.

A regra específica de batalha `7-1-4-1-1-1` também declara que, se o oponente possui zero Life no momento em que se determina que dano será causado ao Leader, o jogador atacante vence. Esse ponto de determinação precisa ser preservado em conjunto com efeitos de substituição que possam impedir o dano antes que ele seja efetivamente determinado.

### OFFICIAL_RULE — Esclarecimento oficial

`EB05-052 Gloriosa` confirma que um efeito “when you would take damage” pode substituir o dano de batalha mesmo quando o jogador está com zero Life e impedir a derrota. O FAQ também confirma a aplicação a dano de `Double Attack` e a dano causado por efeito (`SRC-FAQ-EB05`, EB05-052, p. 2).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-001`: jogador com 0 Life recebe dano de Leader → derrota.
- `T-002`: jogador com 0 Life não recebe dano → não perde apenas por estar com 0 Life.
- `T-003`: jogador chega a 0 cartas no deck → derrota no processamento de regra aplicável.
- `T-004`: os dois jogadores satisfazem uma condição de derrota no início do mesmo processamento → ambos perdem, conforme §9-2-1.
- `T-005`: concessão encerra imediatamente e não é substituível.
- `T-006`: efeito de carta que declara vitória/derrota encerra durante a resolução.
- `T-007`: efeito de substituição válido impede dano em 0 Life → não aplicar a derrota que dependeria daquele dano.

### OPEN_QUESTION — Questões em aberto

- A sequência completa entre determinação de dano, substituições, interrupção por Trigger e processamento de derrota precisa ser validada com um conjunto maior de FAQs específicos. Os exemplos oficiais de EB05-052 e `Double Attack` devem permanecer como testes normativos separados.

---

## 3.2 Princípios fundamentais e impossibilidade de ações

**Registro:** `R-002`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §1-3, pp. 2–3.

### OFFICIAL_RULE — Comportamento oficial

- Quando o texto de carta contradiz o Comprehensive Rules, o texto da carta prevalece (`1-3-1`).
- Uma ação impossível não é realizada. Se um efeito contiver várias ações e algumas forem impossíveis, executam-se tantas quanto forem possíveis (`1-3-2`).
- Se um objeto já estiver no estado solicitado, ele permanece nesse estado e a ação não é realizada (`1-3-2-1`).
- Repetições cujo número seja zero ou negativo não executam ação (`1-3-2-2`).
- Uma proibição prevalece sobre uma instrução que exija a ação proibida (`1-3-3`).
- Quando ambos os jogadores precisam fazer escolhas simultâneas, o jogador do turno escolhe primeiro (`1-3-4`).
- Valores escolhidos para instruções numéricas devem ser inteiros não negativos. `Up to X`, sem mínimo adicional, permite escolher zero (`1-3-5`).
- Valores decimais menores que 1 não podem ser escolhidos; valores informativos negativos são tratados como zero, exceto durante cálculos. Power pode ficar negativo; Cost não pode ficar negativo fora de cálculos (`1-3-6`).
- Os efeitos são processados na ordem em que aparecem no texto (`1-3-7`).
- Quando instruções simultâneas mandam deixar cartas em rested e active, rested prevalece (`1-3-8`).
- O Comprehensive Rules diferencia custo de jogar uma carta e custo de ativar um efeito (`1-3-9`).
- Em ações simultâneas de efeitos, o jogador do turno processa primeiro, depois o não-turno (`1-3-10`).

### INTERPRETATION — Interpretação

A regra de impossibilidade não autoriza ignorar indiscriminadamente um efeito inteiro. É necessário distinguir:
- ação individual impossível dentro de uma sequência de várias ações;
- efeito declarado inválido por outra regra;
- custo de ativação que não pode ser pago;
- condição `if` não satisfeita, que pode impedir os trechos seguintes.

Esses casos têm consequências diferentes e estão detalhados em `R-009` e `R-015`.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-008`: efeito com três ações, sendo a segunda impossível → executar primeira e terceira, quando independentes e permitidas.
- `T-009`: instrução para restar carta já rested → não alterar estado.
- `T-010`: repetição com X = 0 → nenhuma repetição.
- `T-011`: instrução de restar entra em conflito com proibição ativa → a proibição prevalece.
- `T-012`: escolhas simultâneas dos dois jogadores → jogador do turno escolhe primeiro.
- `T-013`: dois efeitos mandam simultaneamente mudar um mesmo objeto para active e rested → resultado rested.
- `T-014`: múltiplas ações de um efeito → preservar a ordem textual.

---

## 3.3 Características, categorias e texto das cartas

**Registro:** `R-003`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§2-1 a 2-17 e 4-9, pp. 3–11; `SRC-GFAQ`, p. 1; `SRC-FAQ-OP16`, OP16-002/003/007/008.

### OFFICIAL_RULE — Comportamento oficial

O capítulo 2 define dados e propriedades das cartas: nome, categoria, cor, tipo, atributo, Power, Cost, Life, Counter, Trigger, número da carta, raridade, Block Symbol e informações de ilustração.

- Categorias como Leader, Character, Event e Stage determinam o tratamento da carta dentro e fora das respectivas áreas (`2-2`).
- Uma carta pode ter várias cores, tipos ou atributos; seus significados são definidos nas seções `2-3`, `2-4` e `2-5`.
- Algumas cartas podem ser tratadas como tendo nomes ou tipos definidos pelo texto; a regra aplicável deve ser considerada mesmo em zonas secretas e em procedimentos de deck construction quando expressamente indicado (`2-1-3` e `2-4`).
- Power e Cost podem ser alterados. O tratamento de Power negativo é diferente do tratamento de Cost negativo (`2-6` e `4-9`).
- Quando mais de um efeito estabelece o `base Power` ou `base Cost`, o Comprehensive Rules define qual valor prevalece para cada característica separadamente (`4-9-2-1` e `4-9-2-2`).
- O texto de Leader, Character e Stage em regra só é válido na área associada à carta, salvo regra ou texto específico em contrário (`2-8`).
- O texto é lido de cima para baixo, e texto entre parênteses normalmente é explicativo, não parte do efeito, exceto quando o próprio texto entre parênteses contém um efeito de jogo (`2-8`).
- `Counter` pertence a Character e pode ter valor modificado; se houver mais de um valor aplicável, a regra determina qual valor utilizar (`2-10`).
- `Trigger` integra o texto da carta, mas sua ativação depende do procedimento específico de dano (`2-11`, `4-6-3` e `10-1-5`).

### OFFICIAL_RULE — Esclarecimentos oficiais

- Uma carta Character com Power 0 ou negativo permanece no campo (`SRC-GFAQ`, p. 1).
- Cost não pode ficar abaixo de zero; quando reduzido abaixo de zero, é tratado como zero (`SRC-GFAQ`, p. 1).
- Power negativo é permitido e afeta a comparação de batalha (`SRC-GFAQ`, pp. 1–2).
- Perguntas de OP-16 confirmam que expressões como “with 8000 power” e “base power 10000” exigem o valor exato nos casos perguntados, não uma comparação “pelo menos” ou “no máximo” (`SRC-FAQ-OP16`, OP16-002/003/007/008). OP17-119, em contraste, confirma uma condição expressa como custo total agregado de até 4 para o conjunto selecionado. Não reduzir todos os filtros numéricos a uma única semântica.

### INTERPRETATION — Interpretação

O simulador não deve tratar todas as características como campos estáticos independentes da partida. Algumas são impressas, outras são derivadas por efeitos, e algumas podem ser contínuas, condicionais ou calculadas. Esta observação descreve o comportamento a ser representado; não define o formato interno.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-015`: Character chega a Power 0 ou negativo → permanece no campo.
- `T-016`: Cost reduzido abaixo de zero → valor efetivo é zero.
- `T-017`: Power permanece negativo e participa normalmente da comparação de batalha.
- `T-018`: duas definições de base Power conflitantes → aplicar a regra de precedência correspondente.
- `T-019`: duas definições de base Cost conflitantes → aplicar a regra de precedência correspondente.
- `T-020`: texto de Character fora da Character Area → não aplicar como se a carta estivesse no campo, salvo exceção explícita.
- `T-021`: carta com nome/tipo/atributo derivado atende a filtro de tipo ou nome quando as regras determinarem que a característica é tratada como tal.

### OPEN_QUESTION — Questões em aberto

- A auditoria de FAQs precisa levantar, por carta, os casos em que “exactly”, “at least”, “up to”, “base” ou “original” são esclarecidos pela Bandai. As perguntas de OP-16 são exemplos e não substituem essa varredura.
- O inventário de dados de carta deverá reconciliar texto original, erratas, FAQ e propriedades derivadas. Essa reconciliação ainda não foi concluída.

---

## 3.4 Zonas, informação pública/privada e mudança de área

**Registro:** `R-004`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§3-1 a 3-10, pp. 6–10; `SRC-GFAQ`, pp. 1 e 6–7.

### OFFICIAL_RULE — Comportamento oficial

O jogo possui as áreas Deck, DON!! Deck, Hand, Trash, Leader Area, Character Area, Stage Area, Cost Area e Life Area (`3-1-1`).

- Leader, Character, Stage e Cost Areas compõem o `field` (`3-1-2`). Expressões como “if you have” ou “on your field” referem-se ao campo conforme a definição oficial (`3-1-2-1`).
- Cada jogador tem uma instância de cada área aplicável (`3-1-3`).
- Algumas áreas são abertas; outras são secretas. O acesso e a visibilidade seguem `3-1-4` e `3-1-5`.
- Quando um Character ou Stage sai de sua área e vai para outra área, é tratado como uma nova carta na nova área; os efeitos que se aplicavam à carta na área anterior deixam de acompanhá-la (`3-1-6`).
- Quando um DON!! muda de área, os efeitos anteriormente aplicados a ele são removidos (`3-1-6-1`).
- Se várias cartas são colocadas simultaneamente numa área, o proprietário decide a ordem, salvo determinação diferente (`3-1-7`).
- Quando várias cartas passam simultaneamente de área aberta para área secreta e o proprietário pode determinar a ordem, o oponente não pode confirmar a ordem escolhida (`3-1-8`).

Propriedades principais das zonas:

| Zona | Regras relevantes |
|---|---|
| Deck | Área secreta, face-down, ordem não pode ser consultada nem alterada, exceto quando permitido. Cartas movidas simultaneamente do deck são movidas uma por vez. Embaralhar significa alterar aleatoriamente a ordem (`3-2`). |
| DON!! Deck | Área aberta, apesar das cartas ficarem face-down; ambos podem consultar conteúdo e ordem e reorganizar o próprio DON!! Deck (`3-3`). |
| Hand | Área secreta para o oponente; o jogador pode ver e reorganizar a própria mão (`3-4`). O FAQ geral confirma que não há limite máximo de cartas na mão (`SRC-GFAQ`, p. 1). |
| Trash | Área aberta, face-up; ambos podem consultar e ordenar, mas cada jogador reorganiza a própria Trash (`3-5`). |
| Leader Area | Área aberta; o Leader colocado ali e tratado como Leader não pode ser movido por efeitos ou regras (`3-6`). |
| Character Area | Área aberta, face-up; contém até cinco Characters. Jogar Character significa colocá-lo nessa área (`3-7`). |
| Stage Area | Área aberta; contém até um Stage (`3-8`). |
| Cost Area | Área aberta; DON!! pode ser consultado, reorganizado e escolhido livremente para pagamento (`3-9`). |
| Life Area | Área secreta, face-down, empilhada; normalmente só a carta do topo pode ser movida. Cartas colocadas face-up são exceção tratada como área aberta (`3-10`). |

### OFFICIAL_RULE — Consequências de zona

- Um card que sai e retorna ao campo é considerado uma nova instância para as regras que dependem de mudança de área (`3-1-6`; ver também `10-2-13-4`).
- Olhar uma zona secreta não move as cartas de suas áreas (`11-3-2`).
- Se o efeito não determinar o que fazer com as cartas vistas, elas retornam à área original, na ordem e estado originais (`11-3-3`).
- Uma carta que precise ser movida de uma zona secreta para outra zona secreta deve ser revelada durante o procedimento, mesmo que o texto não ordene explicitamente a revelação (`11-2-1`).
- Uma carta revelada em zona secreta volta a ficar não revelada após o processamento do efeito ou custo (`11-2-2`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-022`: Character recebe aumento de Power, sai do campo e retorna → efeito que incidia sobre a instância antiga não permanece.
- `T-023`: DON!! muda de área → perde os efeitos aplicados anteriormente a ele.
- `T-024`: efeito move várias cartas simultaneamente para uma área → manter escolha de ordenação do proprietário quando aplicável.
- `T-025`: olhar Life/Deck sem instrução posterior de movimentação → retornar cartas à área, ordem e estado originais.
- `T-026`: buscar carta do Deck para Hand → revelar a carta durante o procedimento.
- `T-027`: Life face-up é tratada como informação aberta, sem perder as demais regras específicas da Life Area.
- `T-028`: DON!! Deck é tratado como área aberta, mesmo com cartas face-down.
- `T-148`: a mão pode conter qualquer quantidade de cartas; não aplicar limite máximo geral.

### OFFICIAL_RULE — Esclarecimento oficial

Um efeito `during this turn` que já foi ativado e resolvido continua válido durante o turno mesmo que a carta que o ativou mude de área (`SRC-GFAQ`, p. 5). Isso não contradiz `3-1-6`: uma coisa é o efeito aplicado à instância da carta que mudou de área; outra é um efeito já criado que continua afetando o jogo por sua duração. OP16-045 confirma ainda que, no caso específico perguntado, um `[On Play]` pode devolver a própria origem à mão e continuar com o trecho posterior que joga outra carta. Isso não deve ser confundido com auto effect cujo timing foi satisfeito, mas cuja ativação ainda não ocorreu: nesse último caso, `8-1-3-1-3` pode impedir a ativação se a origem mudar de área antes.

### OPEN_QUESTION — Questões em aberto

- Casos específicos de movimentação simultânea entre zonas secretas, seleção parcialmente oculta e embaralhamento precisam ser auditados em FAQs de cartas à medida que forem encontrados.
- A regra sobre “player” e “owner” é distinta: `player` designa quem possui/tem a carta no contexto normativo da regra; `owner` designa o titular original (`4-2-1` e `4-2-1-1`). Não assumir que os dois termos são intercambiáveis.

---

## 3.5 Capacidade das áreas Character/Stage e descarte por limite

**Registro:** `R-005`  
**Classificação:** `ENGINE_RULE`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§3-7 e 3-8, pp. 8–9; `SRC-GFAQ`, p. 3.

### OFFICIAL_RULE — Comportamento oficial

- Até cinco Characters podem ocupar a Character Area (`3-7-6`).
- Se o jogador tem cinco Characters e quer jogar outro, revela o novo Character, envia um dos Characters existentes para a Trash e então coloca o novo (`3-7-6-1`).
- Esse descarte por limite é processamento de regra; nenhum efeito pode ser aplicado a ele (`3-7-6-1-1`).
- Até um Stage pode ocupar a Stage Area (`3-8-5`).
- Para substituir Stage: revelar o novo Stage, pagar seu custo, enviar o Stage anterior para a Trash e então colocar o novo. O FAQ geral confirma que o pagamento ocorre antes de enviar o Stage anterior para a Trash (`3-8-5-1`; `SRC-GFAQ`, p. 1).

### Distinção normativa

O Character descartado pelo limite de cinco não é considerado K.O. O FAQ geral declara expressamente que seu `[On K.O.]` não ativa (`SRC-GFAQ`, p. 3). Portanto, “foi para Trash” e “foi K.O.” não são sinônimos.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-029`: tentar jogar sexto Character → escolher um Character existente, enviá-lo diretamente à Trash sem aplicar efeitos e jogar o novo.
- `T-030`: Character removido pelo limite de cinco não ativa `[On K.O.]`.
- `T-031`: substituir Stage → pagar custo antes de mover Stage anterior para Trash.
- `T-032`: Stage substituído por outro Stage não é automaticamente tratado como K.O.

---

## 3.6 Terminologia: player, owner, turn player, active/rested e DON!!

**Registro:** `R-006`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§4-2 a 4-4 e §§6-2/6-5, pp. 9–14; `SRC-GFAQ`, pp. 1 e 7–9.

### OFFICIAL_RULE — Comportamento oficial

- `Player` refere-se à pessoa que possui/tem a carta no contexto da regra; `owner` é o titular original da carta (`4-2-1` e `4-2-1-1`).
- `Turn player` é quem está executando o turno; `non-turn player` é o oponente (`4-3`).
- Cartas nas Leader, Character, Stage e Cost Areas ficam em estado active ou rested (`4-4-1`).
- DON!! dado a uma carta não é active nem rested (`4-4-2`).
- DON!! já dado a uma carta não pode ser transferido diretamente para outra carta (`SRC-GFAQ`, p. 1).
- Na Refresh Phase, DON!! dado a Leader/Character retorna obrigatoriamente à Cost Area rested (`6-2-3`); o jogador não pode escolher mantê-lo anexado (`SRC-GFAQ`, p. 1).
- `DON!! −X` pode selecionar DON!! da Leader Area, Character Area, Stage Area ou Cost Area e devolvê-lo ao DON!! Deck (`10-2-10-1`; `SRC-GFAQ`, pp. 2–3).
- DON!! dado e DON!! no DON!! Deck não podem ser tornados active ou rested por efeitos (`SRC-GFAQ`, p. 7).
- Ao mover a carta que recebeu DON!! para outra área, os DON!! dados retornam à Cost Area rested (`6-5-5-4`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-033`: diferenciar possuidor atual e proprietário original ao devolver cartas.
- `T-034`: DON!! dado não é contado como active ou rested.
- `T-035`: Refresh retorna DON!! dado e o coloca rested na Cost Area.
- `T-036`: não permitir transferir DON!! já dado diretamente a outro Character.
- `T-037`: `DON!! −X` pode selecionar DON!! dado ou não dado, dentro das áreas permitidas.
- `T-038`: efeito não pode mudar para active/rested DON!! dado ou no DON!! Deck.

---

## 3.7 Compra, dano, seleção de `up to` e ações numéricas

**Registro:** `R-007`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§4-5, 4-6 e 4-8, pp. 10–11; `SRC-GFAQ`, pp. 5–6.

### OFFICIAL_RULE — Compra

- `Draw 1` move a carta do topo do deck para a mão, sem revelar (`4-5-1` e `4-5-2`).
- `Draw X` executa o procedimento X vezes; se X = 0, nada acontece (`4-5-3`).
- `Draw up to X` permite encerrar a ação antes de cada compra, até atingir X (`4-5-4`).
- A regra geral de “up to” permite escolher de zero até X imediatamente antes da resolução do efeito (`4-8-1`).

### OFFICIAL_RULE — Dano ao Leader

- Dano ao Leader move uma carta do topo da Life para a mão por ponto de dano (`4-6-2`).
- Se uma carta com `[Trigger]` chegar à mão a partir da Life durante esse procedimento, o jogador pode revelar e ativar o Trigger (`4-6-3`).
- Se a carta de Life não puder ser adicionada à mão por efeito ou substituição, o Trigger não pode ser ativado (`4-6-3-1`).

### OFFICIAL_RULE — Esclarecimentos oficiais

- `Up to` permite escolher zero (`SRC-GFAQ`, p. 6).
- Um efeito com alvo indicado pode ser ativado mesmo que o alvo não esteja presente; nesse caso nada acontece (`SRC-GFAQ`, p. 6).
- Mover uma carta da Life para a mão ou Trash por um efeito que não seja o procedimento de dano não ativa Trigger (`SRC-GFAQ`, p. 5).
- Ao receber 2 danos de `[Double Attack]`, o Trigger da primeira carta é resolvido antes de processar o segundo dano (`SRC-GFAQ`, p. 7).

### Distinções que não devem ser colapsadas

- `up to X`: escolha um número entre 0 e X no momento determinado pelo CR.
- número obrigatório `X`: execute a quantidade exigida, até onde as regras permitirem.
- alvo inexistente: o efeito pode ser ativado, mas a ação dirigida ao alvo não produz resultado.
- custo impossível: pode impedir a ativação do efeito, conforme §8-3.
- efeito inválido: impede que a parte inválida ocorra, a escolha seja feita ou o custo seja pago, conforme §8-2.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-039`: `Draw 0` não compra.
- `T-040`: `Draw 3` compra três vezes.
- `T-041`: `Draw up to 3` pode comprar zero, uma, duas ou três cartas.
- `T-042`: `up to 1` pode selecionar zero.
- `T-043`: alvo ausente permite ativar o efeito, mas não produz a ação direcionada.
- `T-044`: mover Life para a mão por efeito não permite Trigger.
- `T-045`: dano que adiciona Life à mão permite oferecer Trigger.
- `T-046`: substituição que impede adicionar Life à mão impede Trigger.
- `T-047`: em dois danos, resolver Trigger do primeiro antes de continuar o segundo.

---

## 3.8 Jogar carta, valores base e condições textuais

**Registro:** `R-008`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§4-7 a 4-12, pp. 10–11; §§8-3 e 8-4; `SRC-GFAQ`, pp. 1–2; `SRC-FAQ-EB05`, EB05-034 e EB05-046, pp. 2–3.

### OFFICIAL_RULE — Comportamento oficial

- Jogar uma carta envolve pagar seu custo e então ativar/colocar a carta conforme sua categoria (`4-7`).
- “Não pode jogar” significa que não pode pagar o custo e jogar/ativar a carta (`4-7-2`).
- Se vários efeitos definem o `base Power` ou `base Cost`, há regra de precedência pelo maior valor aplicável (`4-9-2`).
- `If` possui semântica de condição: se a cláusula não for satisfeita, o texto posterior não é resolvido (`4-10-1` e `8-3-3`).
- `Then` organiza uma ação posterior; a falha da ação após `then` não deve ser confundida com a falha da condição anterior `if` (`4-10-2`).
- `Remove` significa mover uma carta para outra área (`4-11`).
- `Set Power to 0` reduz o Power pelo valor que o alvo tinha no momento em que o efeito foi ativado; se já estava negativo, nada acontece (`4-12`).

### OFFICIAL_RULE — Esclarecimentos oficiais

- Em EB05-034, a redução de DON!! causada por uma parte do efeito altera a condição da parte subsequente: depois de chegar a seis DON!! ou menos, a segunda parte não pode ser executada (`SRC-FAQ-EB05`, EB05-034, p. 2).
- EB05-046 confirma que um efeito pode consultar o estado após pagar seu custo para determinar a característica resultante (`SRC-FAQ-EB05`, EB05-046, p. 2).
- A FAQ geral confirma que um Character com Power negativo permanece com o valor negativo e que DON!! pode elevar esse valor sem convertê-lo previamente em zero (`SRC-GFAQ`, pp. 1–2).

### INTERPRETATION — Interpretação e cautela

A avaliação de cada condição deve respeitar o momento em que a regra ou o texto exige sua avaliação. Não presumir que todas as condições de um texto composto são congeladas no início, nem que todas são continuamente recalculadas. O caso concreto e a redação oficial determinam isso.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-048`: falha de `if` impede os trechos subsequentes protegidos por essa condição.
- `T-049`: ação após `then` é impossível, mas isso não deve automaticamente invalidar ações posteriores fora da cláusula `if`.
- `T-050`: `Set Power to 0` usa o Power existente na ativação.
- `T-051`: `Set Power to 0` aplicado a alvo já negativo não altera seu Power.
- `T-052`: uma condição avaliada após custo reflete o estado resultante do custo, quando o texto/FAQ estabelece esse timing.
- `T-053`: `Remove` movimenta a carta para outra área e deve acionar as regras de mudança de zona aplicáveis.

### OPEN_QUESTION — Questão em aberto

É necessário continuar levantando exemplos de texto composto em FAQs de sets diferentes para delimitar com precisão quando condições são verificadas na ativação, durante o custo ou durante a resolução. Os casos acima não autorizam uma regra universal de “snapshot” ou de “reavaliação contínua”.

---

## 3.9 Construção de deck e preparação da partida

**Registro:** `R-009`  
**Classificação:** `ENGINE_RULE`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§5-1 e 5-2, pp. 11–12; `SRC-BANLIST`.

### OFFICIAL_RULE — Comportamento oficial

- Cada jogador precisa de exatamente um Leader, um deck de 50 cartas e um DON!! Deck de 10 cartas (`5-1-2`).
- O deck principal contém Character, Event e Stage (`5-1-2-1`).
- Só podem ser incluídas cartas cujas cores estejam presentes no Leader (`5-1-2-2`).
- Não podem existir mais de quatro cartas com o mesmo número de carta no deck (`5-1-2-3`).
- Efeitos específicos de construção de deck substituem as regras gerais pertinentes e são válidos durante a construção (`5-1-2-4` a `5-1-2-4-2`).
- Procedimento de início:
  1. apresentar deck válido;
  2. embaralhar e colocar deck face-down;
  3. colocar Leader face-up;
  4. determinar quem escolhe jogar primeiro ou segundo;
  5. o jogador escolhido declara primeiro/segundo;
  6. resolver efeitos `[At the start of the game]`, respeitando a ordem entre os jogadores;
  7. embaralhar novamente o deck se efeito de início tiver alterado esse deck;
  8. comprar cinco cartas;
  9. permitir mulligan uma vez por jogador, começando pelo primeiro jogador;
  10. colocar a quantidade de Life igual ao Life do Leader, respeitando a ordem do topo;
  11. o primeiro jogador inicia o turno (`5-2-1`).

### Limite de escopo

As regras de deck construction são relevantes para validar uma partida e preparar o estado inicial. Já listas de banimento/restrição e regras por formato são dados de legalidade de formato, separados do processamento de cartas durante a partida.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-054`: rejeitar Leader/deck/DON!! Deck com quantidades incorretas.
- `T-055`: rejeitar carta cuja cor não está no Leader, exceto quando efeito de construção permita.
- `T-056`: rejeitar mais de quatro cópias do mesmo card number, respeitando exceções oficiais.
- `T-057`: resolver efeitos de início de jogo após decisão de primeiro/segundo.
- `T-058`: embaralhar deck novamente quando efeito de início o altera.
- `T-059`: mulligan uma vez por jogador, iniciando pelo primeiro jogador.
- `T-060`: colocar Life com carta do topo do deck no fundo da pilha de Life.
- `T-061`: primeiro jogador inicia a partida após preparação.

### OPEN_QUESTION — Questões em aberto

- O validador de deck deverá decidir quais formatos legais serão oferecidos e aplicar a lista oficial correspondente. Isso é um escopo adicional de validação de formato, não uma justificativa para incorporar regras de torneio ao motor de partida.
- Efeitos de deck construction que alteram limite ou categoria exigem auditoria individual de cartas.

---

## 3.10 Sequência de turno e fases

**Registro:** `R-010`  
**Classificação:** `ENGINE_RULE`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §6, pp. 12–14.

### Ordem do turno

O turno contém, nesta sequência:

1. Refresh Phase;
2. Draw Phase;
3. DON!! Phase;
4. Main Phase;
5. End Phase (`6-1-1`).

### Refresh Phase

Ordem documentada:

1. expiram efeitos que duram `until the start of your next turn` (`6-2-1`);
2. ativam-se efeitos com timing de início de turno (`6-2-2`);
3. DON!! dado a Leader/Character retorna à Cost Area rested (`6-2-3`);
4. cartas rested nas Leader, Character, Stage e Cost Areas tornam-se active (`6-2-4`).

### Draw Phase

O jogador do turno compra uma carta. O primeiro jogador não compra na sua primeira Draw Phase (`6-3-1`).

### DON!! Phase

- normalmente, duas cartas são colocadas face-up na Cost Area;
- na primeira DON!! Phase do primeiro jogador, somente uma é colocada;
- com uma carta restante no DON!! Deck, coloca-se uma;
- com zero cartas, nenhuma é colocada (`6-4`).

### Main Phase

- Efeitos que ativam no início da Main Phase são processados (`6-5-1`).
- Jogar cartas, ativar efeitos, dar DON!! e batalhar podem ocorrer em qualquer ordem e tantas vezes quanto possível/desejado (`6-5-2`).
- Ao declarar o fim da Main Phase, segue-se para End Phase (`6-5-2-1`).

### End Phase

- Primeiro são processados `[End of Your Turn]`; depois `[End of Your Opponent’s Turn]` (`6-6-1-1`).
- Cada efeito dessa categoria só pode ser ativado/resolvido uma vez naquele processamento (`6-6-1-1-1`).
- Se houver vários efeitos `[End of Your Turn]`, o jogador do turno escolhe a ordem; para `[End of Your Opponent’s Turn]`, a escolha cabe ao não-turno (`6-6-1-1-3/4`).
- Depois, expiram os efeitos com duração específica de fim de turno/end phase, primeiro os do jogador do turno e depois os do não-turno (`6-6-1-2`).
- Por fim, expiram os efeitos `during this turn`, na ordem do jogador do turno e depois do não-turno; o turno muda (`6-6-1-3/4`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-062`: validar a ordem das cinco fases.
- `T-063`: expirar `until the start of your next turn` antes dos efeitos de início do turno.
- `T-064`: primeiro jogador não compra na primeira Draw Phase.
- `T-065`: primeiro jogador recebe apenas um DON!! na primeira DON!! Phase.
- `T-066`: DON!! Deck com 0/1/2+ cartas produz a quantidade correta na DON!! Phase.
- `T-067`: ações da Main Phase podem ser intercaladas e repetidas.
- `T-068`: End Phase processa efeitos de fim do turno e do turno do oponente na ordem oficial.
- `T-069`: expiracões de duração seguem as prioridades e a sequência especificadas.

---

## 3.11 Jogar cartas, ativar efeitos e dar DON!! na Main Phase

**Registro:** `R-011`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§6-5-3 a 6-5-6, pp. 13–14; §§2-7, 3-9 e 4-4; `SRC-GFAQ`.

### OFFICIAL_RULE — Comportamento oficial

A Main Phase permite:
- jogar Character e Stage;
- ativar Event;
- ativar efeitos de cartas;
- dar DON!! a Leader e Characters;
- iniciar batalhas.

Ao dar DON!!:
- seleciona-se um DON!! active da Cost Area;
- ele é colocado junto à carta escolhida;
- cada DON!! dado fornece +1000 Power durante o próprio turno do jogador;
- a ação pode ser repetida enquanto houver DON!! disponível;
- se a carta que recebeu DON!! mudar de área, os DON!! retornam à Cost Area rested (`6-5-5`).

### OFFICIAL_RULE — Esclarecimentos oficiais

- Jogar uma carta de custo zero é permitido sem DON!! disponível (`SRC-GFAQ`, p. 1).
- DON!! não pode ser transferido diretamente entre Characters/Leader (`SRC-GFAQ`, p. 1).
- Efeitos como `You may rest 1 of your DON!! cards:` representam custo de ativação pago restando um DON!! active na Cost Area (`SRC-GFAQ`, p. 9).
- DON!! −X seleciona DON!! das áreas especificadas pela regra de keyword, incluindo DON!! dados (`10-2-10`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-070`: jogar carta de custo zero sem DON!! active.
- `T-071`: DON!! dado concede +1000 Power durante o turno próprio, não como aumento permanente.
- `T-072`: mudança de área da carta hospedeira retorna os DON!! rested à Cost Area.
- `T-073`: tentar transferir DON!! diretamente entre cartas é inválido.
- `T-074`: ativação com custo de restar DON!! utiliza apenas DON!! active da Cost Area.
- `T-075`: `DON!! −X` inclui DON!! dados quando a regra da keyword permite.

---

## 3.12 Ataques e batalhas

**Registro:** `R-012`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §7, pp. 14–18; `SRC-GFAQ`, pp. 2–8; `SRC-FAQ-EB05`, EB05-052, p. 2; `SRC-FAQ-OP17`, OP17-040; `SRC-FAQ-ST31`, ST31-003; `SRC-FAQ-ST32`, ST32-005; `SRC-FAQ-OP14-EB04`, EB04-015; `SRC-FAQ-PROMO`, P-001/P-004.

### OFFICIAL_RULE — Condições para atacar

Durante a Main Phase, Leader active ou Character active pode ser rested para atacar:
- o Leader do oponente; ou
- um Character rested do oponente (`7-1`).

Characters não podem atacar no turno em que são jogados, salvo exceção (`3-7-4`). O primeiro turno de cada jogador não permite batalhas (`6-5-6-1`).

### Etapas da batalha

A batalha segue esta sequência:

1. Attack Step;
2. Block Step;
3. Counter Step;
4. Damage Step;
5. End of the Battle (`7-1`).

#### OFFICIAL_RULE — Attack Step

- O atacante é rested e o alvo é escolhido (`7-1-1-1/2`).
- Ativam-se os efeitos associados à declaração de ataque, como `[When Attacking]`, “when you attack” e efeitos de ataque do oponente (`7-1-1-3`).
- FAQs esclarecem que `[On Your Opponent’s Attack]` ocorre depois do `[When Attacking]` do atacante; pode ser ativado mesmo que o oponente esteja atacando um alvo diferente do objeto associado ao efeito (`SRC-GFAQ`, p. 8).
- O efeito de mudança de alvo deve respeitar as restrições sobre o novo alvo (`SRC-FAQ-OP16`, OP16-080).
- `ST32-005` confirma, para o Zoro perguntado, que sua permissão de atacar Characters não se aplica no primeiro turno do jogador; a exceção precisa estar no texto/keyword aplicável e não pode ser ampliada por inferência.
- `EB04-015` confirma que `[Rush]` permite atacar no turno em que o Character é jogado, mas não remove a regra geral de que o alvo Character deve estar rested.

#### OFFICIAL_RULE — Block Step

- O defensor pode ativar no máximo um `[Blocker]` por batalha/Block Step (`10-1-4`; `SRC-GFAQ`, pp. 3–4).
- O Character com Blocker é rested e passa a ser o alvo do ataque, conforme o procedimento do CR.
- Não se pode usar Blocker de um Character rested nem do próprio Character que já está sendo atacado (`SRC-GFAQ`, pp. 3–4).
- A ativação do Blocker é opcional (`SRC-GFAQ`, p. 4).
- `[Unblockable]` impede a ativação de Blocker contra aquele ataque (`10-1-7`).

#### OFFICIAL_RULE — Counter Step

- O defensor pode usar Counters de Character da mão e Event com `[Counter]`, obedecendo aos custos e à ordem do procedimento.
- Counter de Character não tem custo adicional além de usar a carta da mão; o valor de Counter é aplicado ao Leader ou Character correspondente durante a batalha (`2-10`; `SRC-GFAQ`, pp. 2–3).
- Event `[Counter]` só pode ser ativado no Counter Step de uma batalha do oponente, salvo autorização expressa de Trigger ou outro efeito (`10-2-4`).
- Após Blocker, ainda é possível usar Counter (`SRC-GFAQ`, p. 4).
- Quando vários efeitos similares de Counter se aplicam à mesma carta, a FAQ OP17-063/118 manda aplicar somente o valor mais alto, não somar todos os bônus similares (`SRC-FAQ-OP17`, p. 9).
- `ST31-003` demonstra que a condição específica de DON!! dada precisa continuar satisfeita no momento de ativar `[Blocker]`: se efeitos de ataque removerem DON!! dado antes dessa ativação e o total cair abaixo do exigido, Blocker não pode ser ativado. No texto perguntado, a expressão **“total of 3 or more DON!! cards given”** conta DON!! dados ao Leader e a todos os Characters, não apenas à carta que possui o efeito. Isso é diferente da condição `[DON!! xX]` de `8-3-2-3`, que exige DON!! dados à própria carta com o efeito; não colapsar as duas redações.
- A FAQ promocional P-004 esclarece que, depois de um Blocker substituir o alvo, o alvo não retorna automaticamente ao original se o Blocker deixar de ter o DON!! que sustentava uma condição. P-001 também conserva o ataque já declarado no caso específico em que DON!! é removido depois da declaração. Estes são esclarecimentos de janelas específicas, não licença para ignorar condições de ativação antes de uma ação ser declarada.

#### OFFICIAL_RULE — Damage Step

- Compara-se o Power efetivo do atacante e do alvo (`7-1-4`).
- Se o atacante tiver Power igual ou maior, vence a batalha.
- Atacando Leader: causa dano conforme a quantidade determinada; atacando Character: o Character alvo é K.O. (`7-1-4-1`).
- Se o atacante tiver Power inferior, perde a batalha e nada acontece no Damage Step (`7-1-4-2`).
- Quando o dano a Leader é determinado com zero Life, a regra específica `7-1-4-1-1-1` declara vitória do atacante.
- Quando o dano é 1 ou mais e o Leader tem Life no momento da determinação, as cartas do topo da Life são processadas conforme o procedimento (`7-1-4-1-1-2/3`).

#### OFFICIAL_RULE — End of the Battle

- Ativam-se efeitos de fim de batalha e efeitos “if this ... battles” (`7-1-5-2`).
- Expiram efeitos `during this battle`, primeiro os do jogador do turno e depois os do não-turno (`7-1-5-3/4`).
- O jogo retorna à Main Phase (`7-1-5-5`).

### Encerramento antecipado

Se o atacante ou o alvo sair de sua área durante Attack, Block ou Counter Step, a batalha termina ao final da etapa corrente, sem passar à etapa seguinte. O FAQ geral esclarece essa regra (`SRC-GFAQ`, p. 1–2).

### Interações relevantes de Double Attack e Trigger

- `Double Attack` determina dois danos ao Leader em vez de um (`10-1-2`).
- O FAQ geral afirma que, se Trigger do primeiro dano retirar o Character atacante ou remover o efeito Double Attack, o segundo dano determinado ainda é processado (`SRC-GFAQ`, p. 4).
- O Trigger do primeiro dano é resolvido antes da verificação do segundo dano (`SRC-GFAQ`, p. 7).
- O FAQ geral responde que Double Attack não garante vitória se o oponente começa a resolução com uma Life restante (`SRC-GFAQ`, p. 5). Essa resposta deve ser preservada como caso de teste; não simplificar a regra para “cada ponto de dano é um novo ataque”.
- `EB05-052 Gloriosa` confirma que uma substituição “when you would take damage” pode impedir ambos os danos de Double Attack (`SRC-FAQ-EB05`, p. 2).
- `EB03-055 Nico Robin`: o efeito `[On K.O.]` perguntado pode causar dano ao Leader sem abrir Blocker ou Counter, mas, se a carta de Life for adicionada à mão pelo procedimento de dano, ainda pode oferecer Trigger. A FAQ delimita `Banish` ao dano de ataque ao Leader e `Double Attack` ao dano durante um ataque; o efeito de dano perguntado não recebe automaticamente essas keywords. A resposta também explica o resultado no cenário em que o oponente está com zero Life (`EB03`, pp. 2–3).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-076`: impedir ataque com carta rested.
- `T-077`: impedir Character sem exceção de atacar no turno em que foi jogado.
- `T-078`: impedir qualquer batalha no primeiro turno de cada jogador.
- `T-079`: atacar somente Leader ou Character rested do oponente.
- `T-080`: efeitos `[When Attacking]` ativam após declaração do ataque.
- `T-081`: `[On Your Opponent’s Attack]` respeita precedência documentada e não depende necessariamente do alvo ser a carta do efeito.
- `T-082`: apenas um Blocker por batalha; Blocker rested ou o próprio alvo não pode ser usado.
- `T-083`: Counter pode ser usado após Blocker.
- `T-084`: Event `[Counter]` só ativa no Counter Step permitido.
- `T-085`: comparar Power com igualdade favorecendo o atacante.
- `T-086`: atacante com Power menor não causa K.O./dano.
- `T-087`: atacante que perde o alvo/atacante por mudança de área encerra batalha no fim da etapa.
- `T-088`: `Double Attack` mantém a quantidade de dano definida mesmo se Trigger remover o efeito ou a carta atacante.
- `T-089`: Trigger do primeiro dano resolve antes do segundo dano.
- `T-090`: iniciar Double Attack contra 1 Life não deve ser tratado automaticamente como vitória; conferir resultado conforme o FAQ geral.
- `T-091`: substituição de dano pode impedir dano de batalha, dano múltiplo ou dano causado por efeito.

### OPEN_QUESTION — Questões em aberto

- A ordem exata entre várias substituições de dano e a determinação de vitória deve ser complementada com FAQs específicos que tratem de múltiplos efeitos simultâneos.
- Este registro cobre a estrutura de batalha; não pretende enumerar todas as redações especiais que alteram alvo, etapas, quantidade de dano ou permissões de atacar.

---

## 3.13 Classes de efeitos e durações

**Registro:** `R-013`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§8-1 e 8-2, pp. 15–20.

### OFFICIAL_RULE — Tipos de efeito

O Comprehensive Rules identifica quatro categorias:

- **Auto effects:** ativam automaticamente quando ocorre o evento descrito. Se o evento ocorrer novamente, o efeito pode ativar novamente. Se a carta que cumpriu o timing sair de área antes da ativação, o auto effect não ativa (`8-1-3-1`).
- **Activate effects:** podem ser declarados pelo jogador do turno durante a Main Phase, fora de batalha; incluem `[Activate: Main]` e `[Main]` (`8-1-3-2`).
- **Permanent effects:** afetam continuamente o jogo enquanto válidos; podem depender de condição. Alguns efeitos “according to/under the rules” continuam válidos mesmo em área secreta (`8-1-3-3`).
- **Replacement effects:** identificados pela palavra `instead` (`8-1-3-4`).

Os efeitos também podem ser:
- one-shot: produzem mudança no momento da resolução;
- continuous: continuam afetando o jogo durante uma duração (`8-1-4`).

### OFFICIAL_RULE — Prioridade de efeitos permanentes

Quando o efeito permanente altera sua própria aplicação ou a de outro efeito permanente:
1. jogador do turno processa os seus efeitos permanentes em qualquer ordem;
2. não-turno processa os seus em qualquer ordem;
3. se o estado mudar novamente, repete-se o procedimento até não haver novas alterações (`8-1-3-3-5`).

### OFFICIAL_RULE — Regras de substituição

- Se uma substituição está disponível e o jogador escolhe não aplicá-la, ela não é resolvida (`8-1-3-4-1`).
- Havendo várias substituições aplicáveis, a prioridade de `8-1-3-4-2` é: primeiro a substituição da carta que gerou aquele efeito de substituição; depois as substituições do jogador do turno, na ordem que ele escolher; por fim, as do não-turno, na ordem que ele escolher. Não é correto resumir o primeiro nível como “o efeito que criou a situação” sem preservar a redação específica da regra.
- A mesma substituição não pode substituir repetidamente o mesmo processamento individual/situação (`8-1-3-4-3`).
- A substituição troca toda a parte do processamento indicada por ela, mesmo no meio da resolução (`8-1-3-4-4`). **A regra `8-1-3-4-4-1`, omitida no v0.2, acrescenta que, quando o texto é processado de cima para baixo e a substituição ocorre no meio, o texto restante continua sendo executado em ordem depois da substituição.** Isso não significa cancelar automaticamente toda a habilidade.
- Se não for possível executar a substituição, ela não pode ser aplicada (`8-1-3-4-5`).
- Uma substituição pode habilitar outra substituição quando suas condições passam a ser satisfeitas (`8-1-3-4-6`).
- A parte substituída é tratada como ativada pelo proprietário do efeito de substituição (`8-1-3-4-7`).

### OFFICIAL_RULE — Validade e invalidação

- Parte de efeito que esteja inválida não ocorre; escolhas e custos associados à parte inválida não são executados (`8-2-1`).
- Efeitos auto/activate já ativados e resolvidos não são retroativamente invalidados por efeitos posteriores (`8-2-3`).
- Um efeito novo recebido por uma carta não é automaticamente invalidado só porque outro efeito da carta foi invalidado (`8-2-4`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-092`: auto effect ativa uma vez por evento e novamente se o evento ocorrer de novo.
- `T-093`: carta que cumpriu timing sai da área antes da ativação → auto effect não ativa.
- `T-094`: activate effect só pode ser declarado pelo jogador do turno durante Main Phase e fora da batalha, salvo exceção.
- `T-095`: permanent effects são reavaliados até estabilização na ordem do CR.
- `T-096`: `instead` opcional recusado não é resolvido.
- `T-097`: não permitir que o mesmo replacement substitua repetidamente o mesmo processamento.
- `T-098`: substituição pode habilitar outra substituição.
- `T-099`: efeito já resolvido não é retroativamente invalidado.
- `T-100`: efeito novo obtido por carta cujo efeito antigo foi invalidado continua válido, salvo regra contrária.

---

## 3.14 Condições, custos de ativação e sequência de ativação

**Registro:** `R-014`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §8-3 e §8-4, pp. 19–22; `SRC-FAQ-EB05`, EB05-034/046; `SRC-FAQ-OP17`, OP17-001/097/099/119; `SRC-FAQ-ST33`, ST33-001/002/003; `SRC-FAQ-ST35`, ST35-003; `SRC-FAQ-ST36`, ST36-001/002/003; `SRC-FAQ-OP14-EB04`, OP14-020/048.

### OFFICIAL_RULE — Condições

- O texto antes de `:` pode definir custo de ativação (`8-3-1`).
- Várias ações de custo são processadas na ordem textual (`8-3-1-1`).
- Custos adicionais introduzidos por efeitos são processados na ordem em que esses efeitos são resolvidos (`8-3-1-2`).
- Todas as condições do efeito precisam ser satisfeitas (`8-3-2-1`).
- `[DON!! xX]` exige que a carta possua pelo menos X DON!! dados (`8-3-2-3`).
- `[Your Turn]` e `[Opponent’s Turn]` definem o lado do turno aplicável (`8-3-2-4/5`).
- Uma condição `if` não satisfeita impede a resolução do texto posterior conforme `8-3-3`.

### Regra crítica: custo impossível antes vs. durante o pagamento

Há duas situações normativamente distintas:

1. **Antes de ativar:** se não for possível pagar parte ou todo o custo, o custo não pode ser pago e o efeito não pode ser ativado (`8-3-1-3`).
2. **Durante o pagamento:** se as condições estavam satisfeitas, o efeito foi ativado, mas o jogador se torna incapaz de pagar o restante enquanto realiza o pagamento, paga-se o máximo possível. O texto depois de `:` não é resolvido (`8-3-1-3-1`). A regra de `[Once Per Turn]` ainda considera a ativação consumida (`10-2-13-5`).

Essa distinção impede reduzir todos os custos a uma operação genérica “pagar tudo ou não fazer nada”.

### Sequência de ativação e resolução

`8-4-1` define um fluxo que inclui:
1. verificar condições;
2. revelar a carta de origem quando necessário;
3. identificar e pagar o custo de ativação;
4. ativar o efeito;
5. resolver o efeito.

Event cards têm um procedimento específico de ativação: a carta é movida para a Trash conforme a regra aplicável e o efeito é então resolvido (`8-4-2`).

### Escolhas e alvos

- `Up to X` tem o momento específico de escolha definido em `4-8-1`: escolher de zero a X imediatamente antes de o efeito ser processado.
- Para `choose`, `select`, `up to` e expressões similares, `8-4-4` determina que as escolhas sejam feitas no momento requerido durante a resolução. Essa regra geral deve ser lida em conjunto com o timing específico de `4-8-1`.
- Quando a quantidade é fixa, o jogador deve escolher tantas cartas/itens quanto puder, até o número especificado; somente quando a expressão `up to` se aplica pode escolher zero (`8-4-4-1`).
- Se os itens são cartas não reveladas em uma área secreta e a escolha depende de informação que o jogador não pode verificar, ele pode optar por não escolher uma carta daquela área (`8-4-4-2`).
- A regra define quem escolhe e de quem são os cards selecionáveis conforme o efeito (`8-4-4` e `8-4-4-3`).
- Cartas escolhidas do Deck têm suas faces verificadas (`8-4-4-4`).
- `8-4-4-3` estabelece a regra-padrão quando o texto não especifica a seleção: se o efeito se aplica a uma carta, refere-se à carta de origem do efeito; se se aplica a um jogador, refere-se ao jogador daquele efeito. Isso não substitui instruções textuais explícitas nem responde, sozinho, quem escolhe ou move uma carta pertencente ao oponente.
- `8-4-5` limita a ativação de auto effects gerados por movimentação de área aos casos em que a área de destino é aberta; o próprio CR cita `[On K.O.]` como exemplo.
- O §4-10 distingue `if` de `then`: se uma cláusula `if` precedente não puder ser resolvida, a cláusula seguinte também não pode (`4-10-1`); se uma cláusula `then` precedente não puder ser resolvida, a cláusula seguinte ainda pode ser resolvida, desde que não esteja bloqueada por um `if` precedente não satisfeito (`4-10-2`). Portanto, a falha de qualquer ação anterior não deve ser tratada como interrupção universal de todas as ações posteriores.
- FAQs específicos esclarecem, em casos concretos, quem escolhe, quem descarta, quem movimenta cartas e quem ordena cartas pertencentes a outro jogador. Esses papéis devem permanecer separados na descrição normativa.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-101`: custo já impossível antes da ativação → não ativar.
- `T-102`: custo inicialmente pagável torna-se impossível durante o pagamento → pagar o máximo possível, não resolver texto após `:`.
- `T-103`: custo composto respeita a ordem textual.
- `T-104`: custo adicional imposto por outro efeito respeita a ordem de resolução desse efeito.
- `T-105`: condições múltiplas exigem satisfação conjunta.
- `T-106`: condição DON!! exige a quantidade de DON!! dados à própria carta.
- `T-107`: escolha `up to X` ocorre no timing estabelecido.
- `T-108`: escolha e seleção respeitam o jogador autorizado a decidir.
- `T-109`: cartas escolhidas do Deck são reveladas.
- `T-146`: quantidade fixa exige escolher o máximo possível até o limite; `up to` permite escolher zero.
- `T-147`: seleção em área secreta cuja condição depende de informação não revelada pode ser recusada.
- `T-110`: Event usado da mão é enviado à Trash antes da resolução conforme a regra de ativação do Event.

### Esclarecimentos específicos importantes

- `OP17-001`: sem carta na mão, uma condição que exige descartar carta da mão não pode ser paga para ativar o efeito.
- `OP17-097`: um efeito específico de redução de Cost não afeta Characters jogados posteriormente naquele turno; não generalizar esse snapshot para todo efeito contínuo.
- `OP17-099`: quando o oponente escolhe uma opção textual, o FAQ define quem executa e como se encadeiam as ações de descarte/adição de Life.
- `OP17-119`: condição com soma de custos deve ser interpretada como soma total no caso perguntado, e não como verificação independente de cada carta.
- `EB05-034`: alteração do estado durante um trecho anterior pode fazer uma condição subsequente deixar de ser satisfeita.
- `OP16-026`: a ausência de adição de carta à mão no primeiro trecho não impede, no caso perguntado, a execução do trecho posterior que joga um Character.
- `OP16-067` e `OP16-077`: a FAQ confirma que a ação posterior de descartar da mão continua obrigatória mesmo quando nenhuma carta elegível é adicionada à mão na etapa anterior.
- `ST34-002`: não haver DON!! no DON!! Deck não impede, no caso perguntado, a execução da parte que K.O. um Character; inversamente, não haver alvo para K.O. não impede a parte de adicionar DON!!.
- `ST36-003`: a parte de comprar por Trigger pode ser executada sem o tipo exigido no Leader, mas a parte posterior de alterar o base Power do Leader não pode.
- `ST33-001`, `ST33-002` e `ST33-003`: nos casos perguntados, descartar carta da própria mão é custo de ativação; sem carta na mão, o efeito não pode ser ativado, mesmo que a parte posterior pareça capaz de afetar o oponente. Em `ST33-002`, se o oponente tiver cinco ou menos cartas na mão, o jogador ainda paga seu próprio custo; a parte que exigiria descarte do oponente não produz descarte.
- `ST36-001`: o `[On K.O.]` perguntado não pode ser ativado sem carta na mão para pagar o custo de trash.
- `ST36-002`: a habilidade `[On Play]` específica não pode ser ativada durante o turno do oponente.
- `ST35-003`: no texto perguntado, o oponente do jogador que ativou o efeito escolhe uma carta da própria mão para trash; se tiver seis ou menos cartas, essa parte não acontece, mas a instrução separada de trashar as duas cartas do topo do próprio deck continua.
- `OP14-020`: depois de ativar o efeito `[Activate: Main]` perguntado, o jogador não pode jogar Character cards naquele turno; a FAQ também especifica que o custo pode restar um Leader, Character, Stage ou DON!! active próprio, conforme o texto.
- `OP14-048 Shiryu`: no caso perguntado, a instrução de trashar a mão não depende de existir Character elegível para retornar; se a carta jogada era a única da mão, depois de jogá-la a mão fica vazia e a habilidade ainda pode ser ativada para retornar o alvo indicado.

---

## 3.15 Ativação de carta vs. ativação de efeito; resolução simultânea

**Registro:** `R-015`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§8-5 e 8-6, pp. 22–23; `SRC-GFAQ`, p. 6; `SRC-FAQ-OP17`, OP17-040; `SRC-FAQ-OP14-EB04`, OP14-021/027/028/032/035/119; `SRC-FAQ-EB03`, EB03-031.

### OFFICIAL_RULE — Comportamento oficial

- Ativar uma carta e ativar um efeito são conceitos diferentes (`8-5-1`).
- `Card activation` refere-se a usar uma Event card da mão (`8-5-2`).
- `Effect activation` refere-se a ativar o efeito de uma carta (`8-5-3`).
- Assim, uma redação “when you activate an Event” refere-se a usar o Event da mão, não simplesmente a resolver um Trigger do Event em Life (`8-5-4`; `SRC-GFAQ`, p. 6).

### OFFICIAL_RULE — Ordem de resolução

- Se os timings de efeitos dos dois jogadores forem satisfeitos simultaneamente, o jogador do turno resolve primeiro (`8-6-1`).
- Se a resolução do efeito do jogador do turno satisfizer o timing de outro efeito do não-turno, esse efeito do não-turno é resolvido antes de prosseguir com os efeitos pendentes do jogador do turno, conforme o procedimento (`8-6-1`).
- Se timing de efeito ocorrer durante dano, ele pode ser ativado após todo o processamento de dano, com exceção do Trigger, que interrompe temporariamente o processamento (`8-6-2` e `8-6-2-1`).
- `OP17-040 Edward.Newgate` confirma, para a habilidade perguntada, o timing de `[On Your Opponent's Attack]`: ela ocorre junto dos demais efeitos desse timing, antes de `[Blocker]` ou Counter. Não deve ser generalizada para alterar a ordem de efeitos que tenham outro timing.
- `OP14-021`, `OP14-027`, `OP14-028`, `OP14-032`, `OP14-035` e `OP14-119` esclarecem, para as cartas perguntadas, que o timing “when this Character becomes rested” ocorre quando o Character é rested ao declarar ataque; não é um evento posterior independente de `[When Attacking]`.
- `EB03-031 Vinsmoke Reiju`: ativar o efeito `[Main]` de um Event por meio de outra habilidade não é “activating an Event” como carta da mão; portanto, não satisfaz por si só um gatilho que exija ativar a carta Event (`EB03`, p. 2).
- Se uma ativação de carta/efeito cumprir o timing de outro efeito, este último pode ser ativado depois da resolução da carta/efeito anteriormente ativado (`8-6-3`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-111`: Trigger de Event ativado por dano não conta como uso de Event da mão para “when you activate an Event”.
- `T-112`: efeitos simultâneos dos dois jogadores respeitam a prioridade do jogador do turno.
- `T-113`: efeito do não-turno cujo timing é gerado durante a resolução do jogador do turno entra na ordem especificada pelo CR.
- `T-114`: efeitos gerados durante dano aguardam fim do processamento de dano, salvo Trigger.
- `T-115`: Trigger interrompe dano no ponto previsto e só então o processamento continua.
- `T-116`: evento que ativa efeito durante a resolução de uma carta/efeito aguarda o ponto definido por `8-6-3`.

---

## 3.16 Rule processing

**Registro:** `R-016`  
**Classificação:** `ENGINE_RULE`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §9, p. 19; §§1-2 e 7-1-4; `SRC-FAQ-OP15-EB04`, OP15-022.

### OFFICIAL_RULE — Comportamento oficial

- `Rule processing` é processamento executado automaticamente pelas regras quando determinados eventos acontecem (`9-1-1`).
- O processamento ocorre imediatamente quando o evento correspondente acontece, mesmo que outra ação esteja em andamento (`9-1-2`).
- No início do processamento de derrota, todos os jogadores que satisfaçam uma condição aplicável perdem (`9-2-1`).
- A FAQ `OP15-022 Brook` pergunta especificamente pelo Leader Brook: se o deck chega a zero durante o turno e outra habilidade depois adiciona uma ou mais cartas ao deck, o jogador ainda perde no fim daquele turno. Esse resultado é específico da habilidade/cenário perguntado; não reescrever a regra geral de `9-2-1` como se qualquer deck vazio gerasse uma perda adiada até o fim do turno.

### INTERPRETATION — Interpretação

Não se deve implementar `rule processing` como sinônimo de resolução de efeitos. O CR define procedimentos automáticos próprios, que podem interromper ou ocorrer entre etapas de outras ações.

O resultado de um evento deve ser avaliado no momento normativo correspondente. Não presumir que toda consequência é postergada até o fim de uma cadeia de efeitos.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-117`: condição de derrota aciona processamento no timing especificado, mesmo quando há outra ação em andamento.
- `T-118`: derrota simultânea avalia todos os jogadores no início daquele processamento.
- `T-119`: processamento de regra e resolução de efeito não são confundidos na ordenação.

---

## 3.17 Keyword Effects e Keywords

**Registro:** `R-017`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§10-1 e 10-2, pp. 19–27; `SRC-GFAQ`, pp. 2–9.

### Keyword Effects

O CR define os efeitos:

- `[Rush]`: permite que Character ataque no mesmo turno em que foi jogado (`10-1-1`).
- `[Double Attack]`: faz o ataque ao Leader causar dois danos em vez de um (`10-1-2`).
- `[Banish]`: durante dano de ataque ao Leader, envia a carta de Life à Trash em vez de adicioná-la à mão; Trigger não ativa (`10-1-3`).
- `[Blocker]`: permite substituir o alvo do ataque por um Character com Blocker, seguindo a etapa e restrições aplicáveis (`10-1-4`).
- `[Trigger]`: pode ser ativado quando uma carta com Trigger é adicionada à mão a partir da Life durante dano (`10-1-5`). Se for ativado, a própria carta não pertence a nenhuma área durante a resolução do Trigger e depois vai para a Trash, salvo instrução contrária (`10-1-5-3`). Uma carta com Trigger colocada face-up na Life também pode ativar Trigger, conforme o FAQ geral (`SRC-GFAQ`, p. 6). FAQs de OP14 confirmam que a carta não pode ser tratada como se já estivesse na Trash para ser jogada da Trash enquanto seu próprio Trigger ainda está sendo resolvido (OP14-100/102/109/110/111, pp. 6–7).
- `[Rush: Character]`: permite atacar Characters do oponente no turno em que a carta foi jogada (`10-1-6`).
- `[Unblockable]`: impede Blocker contra aquele ataque (`10-1-7`).

### Keywords de timing e conceitos

O capítulo 10 também define:
- `K.O.` (`10-2-1`);
- `[Activate: Main]` (`10-2-2`);
- `[Main]` (`10-2-3`);
- `[Counter]` (`10-2-4`);
- `[When Attacking]` (`10-2-5`);
- `[On Play]` (`10-2-6`);
- efeitos de fim de turno (`10-2-7` e seguintes);
- `[DON!! xX]`, `DON!! −X`, `[Your Turn]`, `[Opponent’s Turn]`;
- `[Once Per Turn]`;
- `Trash`;
- `[On Block]` e `[On K.O.]`.

### K.O. não é sinônimo de Trash

- `K.O.` designa Character enviado à Trash por perder uma batalha ou por efeito de carta (`10-2-1-1`).
- Como instrução, significa mover Character da Character Area para a Trash de seu proprietário (`10-2-1-2`).
- `[On K.O.]` e efeitos como “cannot be K.O.’d” só se aplicam quando a carta é K.O.’d por efeito ou batalha. Envio à Trash por outro método não é K.O. (`10-2-1-3`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-120`: Rush permite atacar no turno de jogo.
- `T-121`: Rush: Character permite atacar Character adversário no turno de jogo, não Leader por essa exceção.
- `T-122`: Banish envia Life à Trash sem Trigger.
- `T-123`: Blocker altera alvo e respeita limite por batalha.
- `T-124`: Unblockable impede Blocker.
- `T-125`: Trigger só ativa pelo procedimento de dano que adiciona Life à mão.
- `T-126`: Character enviado à Trash por método que não é K.O. não ativa On K.O.
- `T-127`: `[Main]` e `[Counter]` obedecem às janelas de ativação específicas.
- `T-128`: DON!! xX, DON!! −X e restrições de turno seguem seus próprios critérios.

---

## 3.18 Once Per Turn e On K.O.

**Registro:** `R-018`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §§10-2-13 e 10-2-17, pp. 25–27; `SRC-GFAQ`, p. 3; `SRC-FAQ-EB05`, EB05-061; `SRC-FAQ-OP17`, OP17-015/095.

### Once Per Turn

- O efeito só pode ser ativado e resolvido uma vez durante o turno (`10-2-13-1`).
- Se várias cartas possuírem o mesmo efeito, cada carta possui sua própria oportunidade (`10-2-13-2`).
- Depois que o efeito é resolvido, ele não pode ser ativado novamente naquele turno; o custo de ativação também não pode ser pago novamente (`10-2-13-3`).
- Se a carta sair da área e voltar ao campo, é tratada como nova carta e pode ativar novamente (`10-2-13-4`).
- Se o efeito foi ativado e o pagamento do custo se tornou impossível durante o processo, não pode ativar de novo naquele turno (`10-2-13-5`).

### On K.O.

- Quando o Character com `[On K.O.]` é K.O.’d no campo, verifica-se o timing/condições e o efeito é ativado ainda no campo.
- Depois, a carta vai para a Trash.
- O efeito é resolvido enquanto a carta está na Trash (`10-2-17-1`).
- Essa mudança de área entre ativação e resolução é exceção ao comportamento de outros auto effects (`10-2-17-2`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-129`: mesma carta não usa Once Per Turn duas vezes no mesmo turno após resolução.
- `T-130`: duas cópias da carta podem ativar uma vez cada.
- `T-131`: sair e retornar ao campo cria nova instância para Once Per Turn.
- `T-132`: Once Per Turn consumido mesmo quando o custo se torna impossível durante pagamento.
- `T-133`: On K.O. ativa no campo, move a carta à Trash e resolve na Trash.
- `T-134`: descarte comum não dispara On K.O.
- `T-135`: condição de On K.O. é avaliada no timing específico do K.O., não depois de mover a carta para Trash.

---

## 3.19 Loops infinitos, revelação e consulta de áreas secretas

**Registro:** `R-019`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-CR`, §11, p. 27–28.

### Loops infinitos

- Se nenhum jogador puder interromper um loop infinito, a partida termina em empate (`11-1-1-1`).
- Se só um jogador puder pará-lo, ele declara quantas vezes deseja executar o ciclo; o processamento termina em um ponto em que pode parar (`11-1-1-2`).
- Se ambos puderem parar, o jogador do turno escolhe primeiro a quantidade, depois o não-turno; executa-se o menor número escolhido e o jogo termina no ponto em que o jogador que escolheu o menor valor pode parar (`11-1-1-3`).
- Os jogadores não podem declarar que querem reiniciar um loop quando o estado completo retorna ao estado anterior, salvo quando forem obrigados a fazê-lo.

### Revelar e olhar cartas

- Carta que precisa passar de uma área secreta para outra área secreta é revelada durante a movimentação, mesmo sem instrução explícita (`11-2-1`).
- Carta revelada em área secreta volta a ficar não revelada depois do processamento (`11-2-2`).
- Efeitos que permitem olhar áreas secretas aplicam-se ao jogador do efeito, salvo especificação contrária (`11-3-1`).
- As cartas permanecem nas áreas originais enquanto são olhadas (`11-3-2`).
- Se não houver outra ação indicada, retornam à área, ordem e estado originais (`11-3-3`).

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-136`: loop impossível de interromper por ambos → empate.
- `T-137`: somente um jogador pode interromper → usar a quantidade declarada e parar no ponto permitido.
- `T-138`: ambos podem interromper → aplicar as escolhas na ordem e o mínimo previsto.
- `T-139`: consultar cartas secretas não as move.
- `T-140`: carta selecionada para movimentação entre áreas secretas é revelada no procedimento e volta ao estado oculto quando aplicável.

---

## 3.20 Replacement effects e substituição de processamento parcial

**Registro:** `R-024`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§8-1-3-4-1 a 8-1-3-4-7, especialmente `8-1-3-4-4-1`; FAQs `OP17-015/021/023/095`, `OP15-009/035/069/090/098`.

### OFFICIAL_RULE

A palavra `instead` identifica um replacement effect. Se mais de uma substituição puder aplicar-se à mesma situação, a prioridade segue `8-1-3-4-2`: substituição da carta que gerou aquela substituição; substituições do jogador do turno na ordem escolhida; e, depois, substituições do não-turno na ordem escolhida. Uma substituição não pode ser reaplicada repetidamente ao mesmo processamento individual. Ela substitui toda a parte indicada no texto, e não necessariamente a habilidade inteira. Quando o texto é processado de cima para baixo, as instruções restantes continuam depois da substituição (`8-1-3-4-4-1`). Uma substituição impossível não pode ser aplicada; uma substituição opcional recusada não é resolvida; e uma substituição pode tornar possível outra substituição (`8-1-3-4-1`, `-5` e `-6`).

### Esclarecimentos específicos de FAQ

- `OP17-015 Marco`: quando Marco e outro Character seriam K.O.'d simultaneamente por efeito do oponente, Marco pode substituir a própria remoção por seu próprio K.O.; no cenário perguntado, só Marco é K.O.'d e o outro Character permanece. A FAQ também admite o uso quando a própria carta seria devolvida à mão ou ao fundo do deck (`OP17-015`, p. 1).
- `OP17-021 Crone Oli`: para dois Characters com o tipo indicado que sairiam simultaneamente, o caso perguntado exige restar apenas uma carta, e ambos permanecem (`OP17-021`, p. 1).
- `OP17-023 Nami`: quando dois Characters com os tipos indicados seriam K.O.'d simultaneamente, a substituição perguntada pode restar a própria Nami; os dois Characters permanecem (`OP17-023`, p. 1).
- `OP17-095 Roronoa Zoro`: quando dois Characters do jogador sairiam simultaneamente por efeito do oponente, a resposta permite colocar **3** cartas da Trash no fundo do deck; se fizer isso, nenhum dos dois Characters sai. Não exige seis cartas (`OP17-095`, p. 5).
- `OP15-009 Koby`: diante da saída simultânea de dois Characters com base Power 7000 ou menos, o jogador pode dar apenas −2000 Power ao próprio Leader (não −4000) e manter ambos; ou não substituir e deixar ambos saírem (`OP15-009`, p. 1).
- `OP15-035 Laboon`: diante da saída simultânea de dois Characters com base Power 7000 ou menos, pode restar **2** cartas próprias (não 4) e manter ambos; ou não substituir (`OP15-035`, p. 5).
- `OP15-052 Leo`: diante da saída simultânea de dois Characters com base Power 7000 ou menos, pode colocar **1** Character próprio no fundo do deck de seu proprietário (não dois) e manter ambos; ou não substituir (`OP15-052`, p. 6).
- `OP15-069 Nola`: diante da saída simultânea de dois Characters com base Power 7000 ou menos, pode devolver **1** DON!! do campo ao DON!! Deck (não dois) e manter ambos; ou não substituir (`OP15-069`, p. 8).
- `OP15-090 Perona`: diante da saída simultânea de dois Characters com base Power 7000 ou menos, pode descartar **1** carta da mão (não duas) e manter ambos; ou não substituir (`OP15-090`, p. 10).
- `OP15-094 Roronoa Zoro`: se dois Characters {Straw Hat Crew}, exceto o próprio Zoro, sairiam simultaneamente por efeito do oponente, pode escolher que o próprio Zoro seja removido em substituição (`OP15-094`, p. 11).
- `OP15-098 Monkey.D.Luffy`: diante da saída simultânea de dois Characters {Sky Island} com base Power 6000 ou mais, pode adicionar **1** carta do topo da Life à mão e manter ambos; com zero Life, não pode aplicar essa substituição (`OP15-098`, p. 11).
- `OP15-105 Jewelry Bonney`: diante da saída simultânea de dois Characters com base Power 7000 ou menos, pode adicionar **1** carta do topo da Life à mão (não duas) e manter ambos; ou não substituir (`OP15-105`, p. 12).
- `OP17-021 Crone Oli` e `OP17-023 Nami`: as respostas também abrangem saídas/K.O. simultâneos de dois Characters sob os tipos indicados. Para Crone Oli, basta restar **1** carta própria; para Nami, pode restar a própria Nami, e os dois Characters não são K.O.'d (`OP17-021/023`, p. 1).

### INTERPRETATION — Limite de generalização

Os exemplos demonstram que o evento substituído pode ser uma operação que afeta várias cartas, e que o número de cartas que sairiam não determina, por si só, o número de pagamentos. O texto do replacement effect define sua abrangência e seu custo. Não existe base para concluir que um único custo sempre salva qualquer quantidade de cartas, ou que todas as cartas de um mesmo tipo de situação são abrangidas.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-149`: replacement aplicado no meio de um efeito substitui o trecho indicado e o restante do texto é processado em ordem (`8-1-3-4-4-1`).
- `T-150`: duas substituições aplicáveis seguem a prioridade de `8-1-3-4-2`, não uma ordem arbitrária.
- `T-151`: a mesma substituição não substitui repetidamente a mesma situação.
- `T-152`: substituição que torna válida a condição de outra pode habilitar a segunda substituição.
- `T-153`: substituição impossível não é aplicada; opção recusada não é resolvida.
- `T-154`: teste individual para cada FAQ de remoção simultânea, respeitando o texto, o custo e os tipos/categorias indicados pela carta.

### OPEN_QUESTION

Os FAQs examinados não esgotam todas as combinações possíveis de múltiplas substituições sobre um evento coletivo. A prioridade do CR é geral, mas a composição concreta de eventos e custos de substituição exige manter testes individualizados.

---

## 3.21 Cláusulas `if`, `then` e ações posteriores

**Registro:** `R-025`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§1-3-2, 4-10-1/2, 8-3-3 e 8-4; FAQs `OP16-026/067/077`, `ST34-002`, `ST36-003`, `EB05-034/046`.

### OFFICIAL_RULE

- Uma ação impossível não é executada; em efeitos com várias ações, realizam-se as ações possíveis (`1-3-2`).
- Se uma cláusula `if` precedente não puder ser resolvida, a cláusula seguinte também não poderá (`4-10-1`).
- Se uma cláusula `then` precedente não puder ser resolvida, a cláusula seguinte ainda poderá ser resolvida; a exceção é quando existe `if` precedente não satisfeito (`4-10-2`).
- As condições expressas do efeito precisam ser satisfeitas para sua ativação (`8-3-2`); condições e custos não são intercambiáveis.

### Esclarecimentos específicos de FAQ

- `OP16-026 Emporio.Ivankov`: não adicionar carta à mão na primeira parte não impede, no texto perguntado, jogar até um Character de Cost 2 ou menos na parte seguinte.
- `OP16-067 Tsuru` e `OP16-077 "Buddha" Sengoku`: a FAQ confirma que o descarte da mão permanece obrigatório mesmo quando a busca/revelação não adiciona a carta elegível indicada na parte anterior.
- `ST34-002 Charlotte Cracker`: sem alvo elegível para K.O., ainda pode ser executada a parte de adicionar/restar DON!!; sem cartas no DON!! Deck, a parte de K.O. ainda pode ocorrer.
- `ST36-003 Scratchmen Apoo`: o Trigger pode comprar mesmo sem Leader com `{Supernovas}`, mas a alteração subsequente de base Power do Leader não se resolve.
- `EB05-034`: a redução anterior de DON!! pode fazer com que uma condição posterior deixe de ser satisfeita; a parte dependente não é executada. `EB05-046` esclarece a consulta do estado posterior ao custo no caso específico perguntado.

### INTERPRETATION — Limite de generalização

“Uma ação anterior falhou” é uma descrição insuficiente para prever o restante da habilidade. É necessário determinar se o texto contém condição `if`, relação `then`, `if you do`, instruções independentes, custo ou uma ação impossível. As FAQs demonstram casos distintos, não uma regra única para todo texto composto.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-155`: `if` precedente não satisfeito impede a cláusula seguinte.
- `T-156`: uma cláusula `then` impossível não impede automaticamente a cláusula subsequente, conforme `4-10-2`.
- `T-157`: trecho independente continua quando a FAQ da carta confirma a independência, como OP16-026.
- `T-158`: custo/ação obrigatória posterior continua obrigatória nos casos OP16-067 e OP16-077 mesmo sem busca bem-sucedida.
- `T-159`: ações do caso ST34-002 são testadas separadamente quando a outra parte não tem alvo/recursos.

---

## 3.22 Escolha, controle da seleção e propriedade das cartas

**Registro:** `R-026`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§3-1-7/8, 4-2, 8-4-4-1 a 8-4-4-4; `SRC-FAQ-OP16`, OP16-047/074, pp. 4–5; `SRC-FAQ-OP17`, OP17-047/049/053/075/099/112, pp. 2–7; `SRC-FAQ-EB05`, EB05-028, p. 2; `SRC-FAQ-ST35`, ST35-003, p. 1; `SRC-FAQ-OP15-EB04`, OP15-023, p. 4.

### OFFICIAL_RULE

O CR define o momento da escolha e a quantidade, mas o texto pode determinar um jogador específico. Quando várias cartas são colocadas simultaneamente em uma área, o proprietário decide a ordem, salvo instrução em contrário (`3-1-7/8`). Quando o texto não especifica o item escolhido, a regra-padrão de `8-4-4-3` aplica o efeito à carta de origem ou ao jogador do efeito, conforme o objeto afetado. Isso não deve ser confundido com a regra da área secreta, com o jogador que seleciona, nem com o proprietário da carta.

### Esclarecimentos específicos de FAQ

- `OP16-047`: quando cartas são colocadas no fundo do deck, o proprietário das cartas escolhe quais serão colocadas e em que ordem (`OP16-047`, p. 4).
- `OP16-074`: o proprietário dos DON!! que retornam ao DON!! Deck escolhe quais DON!! serão devolvidos (`OP16-074`, p. 5).
- `OP17-047 Shiki`: o oponente de quem ativou o efeito escolhe uma carta da própria mão e a coloca no fundo do próprio deck (`OP17-047`, p. 2).
- `OP17-049 Charlotte Linlin`: se o oponente escolhe a opção “Draw 2”, quem compra é o jogador que ativou o efeito; se escolhe a opção que manda o oponente descartar duas cartas, o oponente escolhe e descarta da própria mão. A FAQ permite escolher essa opção com uma ou zero cartas: com uma, descarta a única; com zero, nada é descartado (`OP17-049`, pp. 2–3).
- `OP17-053 Barbell`: o oponente de quem ativou o `[On K.O.]` escolhe duas cartas da própria mão e coloca-as no fundo do próprio deck (`OP17-053`, p. 3).
- `OP17-075 X.Drake`: o jogador que ativou o `[On Play]` escolhe, sem olhar, uma carta da mão do oponente para ser descartada (`OP17-075`, p. 4).
- `OP17-099 Charlotte Linlin`: a FAQ distingue o custo inicial, o descarte adicional durante a resolução e a adição de Life. No cenário em que o custo inicial deixa a mão vazia, a parte `Then, add up to 1...` ainda permite adicionar até uma carta, embora não haja carta para o descarte intermediário (`OP17-099`, pp. 5–6).
- `OP17-112 Charlotte Linlin`: o jogador que ativou o `[On Play]` escolhe qual das duas opções do texto será aplicada (`OP17-112`, p. 7).
- `EB05-028 Boa Hancock`: no cenário perguntado, é o oponente quem escolhe quatro cartas da própria mão para trash; não presumir que o jogador que ativou o efeito seleciona as cartas (`SRC-FAQ-EB05`, p. 2).
- `ST35-003`: o oponente escolhe uma carta da própria mão para trash no caso perguntado; quando ele tem seis ou menos, a parte do descarte dele deixa de ocorrer, sem cancelar a ação distinta de trashar cartas do topo do deck do jogador que ativou (`SRC-FAQ-ST35`, p. 1).
- `OP15-023`: o ativador escolhe o DON!! na Cost Area do oponente, mas o DON!! continua ligado ao seu proprietário e não pode ser transferido para Leader/Characters do outro jogador (`OP15-023`, p. 4).

### IMPLEMENTATION_NOTE

A linguagem normativa deve conservar papéis diferentes: jogador que ativa, jogador que escolhe, jogador que realiza a ação, proprietário da carta, jogador controlador da área e destinatário da movimentação. O CR e os FAQs mostram que esses papéis podem não coincidir.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-160`: quando cartas de vários proprietários são colocadas no fundo de decks diferentes, cada proprietário escolhe e ordena suas próprias cartas no caso OP16-047.
- `T-161`: o proprietário escolhe os DON!! retornados em OP16-074.
- `T-162`: escolha de opção, seleção de carta e execução do descarte permanecem atribuídas aos jogadores definidos pelo texto/FAQ OP17-099.
- `T-163`: ativador pode escolher DON!! da Cost Area do oponente em OP15-023, sem mudar o proprietário nem o lado destinatário do DON!!.

---

## 3.23 Localização da carta de origem durante a resolução e processamento de Trigger

**Registro:** `R-027`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§8-1-3-1-3, 8-4-5 e 10-1-5-3; FAQs `OP16-045`, `OP14-100/102/109/110/111`.

### OFFICIAL_RULE

Se a carta que cumpriu o timing de um auto effect sair da área antes de o efeito ser ativado, o efeito não ativa (`8-1-3-1-3`). Para efeitos de Trigger ativados a partir da Life, a carta-fonte não pertence a nenhuma área enquanto o Trigger é resolvido e vai para a Trash depois, salvo texto contrário (`10-1-5-3`). Auto effects decorrentes de mudança de área só ativam quando a área de destino é aberta (`8-4-5`).

### Esclarecimentos específicos de FAQ

- `OP16-045 Crocodile`: o próprio Character pode retornar à mão pelo seu `[On Play]` e, ainda assim, a parte posterior do efeito pode jogar outro Character no caso perguntado.
- `OP14-100 Absalom`, `OP14-102 Kumacy`, `OP14-109 Victoria Cindry`, `OP14-110 Dr. Hogback` e `OP14-111 Perona`: não podem usar o próprio Trigger para jogar a si mesmos da Trash; a carta não é considerada na Trash durante a resolução do Trigger (OP14-EB04, pp. 6–7).
- `OP14-079 Gecko Moria`: a FAQ também ordena o efeito da origem e o `[On K.O.]` do Character que ela própria K.O.'d no cenário perguntado; isso deve ser registrado como um caso específico de sequência de ativações.

### INTERPRETATION

Existem pelo menos três estados normativos diferentes: (a) auto effect ainda não ativado, cuja origem muda de área antes da ativação; (b) efeito já ativado, cuja resolução continua mesmo que a origem mude; e (c) Trigger da Life cuja carta-fonte fica temporariamente sem área. Tratar todos como “a carta saiu, então o efeito para” ou como “o efeito sempre continua” é incorreto.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-164`: auto effect ainda não ativado deixa de ativar se a origem sair da área antes da ativação (`8-1-3-1-3`).
- `T-165`: efeito já ativado continua no caso FAQ OP16-045 mesmo após a origem retornar à mão.
- `T-166`: Trigger em resolução não tem carta-fonte em nenhuma área; não é possível selecioná-la da Trash como no caso OP14-100/102/109/110/111.
- `T-167`: depois de resolver Trigger, a carta-fonte vai para Trash, salvo instrução específica em contrário.

---

## 3.24 Condições de ataque, Blocker e persistência do alvo

**Registro:** `R-028`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§7-1-1, 7-1-2, 10-2-16; FAQs `ST31-003`, Promotion Cards P-001/P-004, `OP17-040`.

### OFFICIAL_RULE

O CR define as janelas de ataque, Blocker e `[On Your Opponent's Attack]`. O Blocker só pode ser usado uma vez por batalha, durante Block Step, e seu uso é uma ação naquele momento. Condições para ativar um efeito devem ser verificadas no timing normativo da ativação; a regra não autoriza reabrir retroativamente uma ação já declarada porque um requisito mudou depois.

### Esclarecimentos específicos de FAQ

- `ST31-003`: no cenário perguntado, um efeito `[When Attacking]` remove DON!! dado de Characters antes da ativação de Blocker; se o número total de DON!! dados ficar abaixo de 3, Blocker não pode ser ativado.
- Promo P-001: no caso perguntado, retirar DON!! após a declaração do ataque não desfaz o ataque já declarado.
- Promo P-004: após Blocker redirecionar o ataque, perder o DON!! que sustentava a condição não faz o alvo voltar automaticamente ao alvo original.
- `OP17-040`: a habilidade especial perguntada ativa no timing de `[On Your Opponent's Attack]`, antes de Blocker ou Counter.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-168`: a condição de Blocker é verificada no timing em que a ativação é oferecida, após os efeitos de ataque pertinentes do caso ST31-003.
- `T-169`: ataque já declarado permanece no caso Promo P-001 após a alteração de DON!! perguntada.
- `T-170`: alvo redirecionado por Blocker não reverte no caso Promo P-004 quando perde DON!! depois.
- `T-171`: o timing OP17-040 ocorre antes de Blocker/Counter no caso da carta perguntada.

### OPEN_QUESTION

Esses FAQs não respondem a todas as formas de alteração posterior do poder, custo, efeito, permissão de ataque ou elegibilidade de alvo. Cada nova redação deve ser confrontada com sua própria janela de ativação e com as regras de batalha.

---

## 3.25 Duração e escopo dos efeitos contínuos; características efetivas

**Registro:** `R-029`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE` + `INTERPRETATION`  
**Fonte:** `SRC-CR`, §§2-6, 4-9, 8-1-3-3 e 8-4-6; FAQs `OP17-008/085/097/119`, `OP14-001/098`, `ST34-004`, `OP16-002/003/007/008`, `ST33-004`, `EB05-010`.

### OFFICIAL_RULE

Efeitos permanentes válidos afetam continuamente o jogo; efeitos podem ter condições e duração. Alterações de `base Power`/`base Cost` seguem as regras de precedência do CR; o valor efetivo pode ainda receber outras modificações. Valores de Power podem ser negativos. Uma mudança de área pode criar nova instância e encerrar modificações aplicadas à carta na área antiga (`3-1-6`).

### Esclarecimentos específicos de FAQ

- `ST34-004`: quando dois efeitos perguntados definem base Power do mesmo Character, o maior valor prevalece no caso concreto.
- OP14-001: trocar base Power não é o mesmo que trocar Power final; o modificador de Power já aplicado continua incidindo no novo base Power, e a FAQ mantém o valor trocado mesmo que uma das cartas saia no cenário perguntado (OP14-EB04, p. 1).
- `OP17-008`: estabelece a interação concreta entre uma definição de base Power já vigente e outro efeito que altera o base Power/Power do Leader.
- `OP17-085` e `OP17-119`: efeito `+12 cost` mencionado nas perguntas só afeta o Character enquanto ele está no campo; não atribui esse Cost enquanto ele está na mão, Trash ou Deck.
- `OP17-097` e `OP14-098`: os efeitos de Cost perguntados não passam a afetar Characters jogados depois da ativação/da aplicação no caso concreto. Isso não deve ser transformado em regra de snapshot para todo efeito contínuo; depende do texto e da resposta específica.
- `OP16-002/003/007/008`: exemplos de filtros que exigem Power ou base Power exatos, e não limites inclusivos genéricos.
- `OP17-119`: permite escolher vários Characters se a soma dos Costs satisfizer o limite 4 no caso perguntado; permite escolher zero (`OP17-119`, p. 9).
- `ST33-004 Borsalino`: o Cost do Character que está na mão é reduzido em 3 no caso perguntado quando uma carta da mão do jogador é trashada pelo efeito do oponente; trata-se de uma condição e de uma zona específicas, não de uma redução global de Cost.
- `EB05-010 Nico Robin`: o FAQ distingue o Blocker que a carta possui originalmente, o Blocker concedido por efeito contínuo e o Blocker concedido por Trigger, e responde de modo diferente quando o efeito que concedeu Blocker é negado. Não transformar o resultado de uma origem de keyword em regra universal para as demais formas de concessão.

### IMPLEMENTATION_NOTE — Cenários de teste derivados

- `T-172`: dois efeitos que definem base Power seguem o maior valor no caso ST34-004.
- `T-173`: base Power e Power efetivo são calculados distintamente no caso OP14-001.
- `T-174`: aumento de Cost restrito ao campo não altera Cost da carta em mão/Trash/Deck no caso OP17-085/119.
- `T-175`: efeito não afeta Character jogado posteriormente quando o FAQ da carta confirma esse escopo (OP17-097/OP14-098).
- `T-176`: filtros `exactly`/`with X power` e `base power X` respeitam valor exato nos casos OP16.
- `T-177`: condição de soma de Costs é avaliada sobre o conjunto selecionado no caso OP17-119.

---

## 3.26 Counter modifiers similares

**Registro:** `R-030`  
**Classificação:** `BOTH`  
**Natureza:** `OFFICIAL_RULE`  
**Fonte:** `SRC-FAQ-OP17`, OP17-063 e OP17-118, p. 9; `SRC-CR`, §§2-10 e 7-1-3.

A FAQ OP17-063/118 responde que, quando múltiplos efeitos similares `Counter +1000` e `Counter +2000` se aplicam à mesma carta durante o Counter Step, somente o valor mais alto é aplicado. O inventário não deve somar os bônus similares por padrão. Esta conclusão permanece circunscrita aos efeitos similares cobertos pela resposta; interações com textos que não são modificadores Counter similares precisam ser verificadas pela redação e pelas regras aplicáveis.

**IMPLEMENTATION_NOTE — Cenário:** `T-178`: com dois modificadores Counter similares aplicáveis, usar somente o de maior valor no caso OP17-063/118.

## 4. Erratas e texto vigente das cartas

**Classificação:** a errata define o texto oficial que deve ser lido no jogo; a execução do efeito é `BOTH`, mas o registro do texto corrigido é dado de carta, não uma nova regra geral de processamento.

Esta seção relaciona as correções verificadas nesta auditoria. Não é ainda a transcrição integral do hub de erratas nem a reconciliação com todas as cartas do catálogo. A lista central da Bandai contém muitas entradas históricas; comunicados avulsos de novos produtos também podem existir fora do hub.

### 4.1 Regra de precedência

A [página oficial Errata Cards](https://en.onepiece-cardgame.com/rules/errata_card/) declara que o texto `After` aplica-se a todos os formatos e prevalece sobre o texto original. Os comunicados avulsos de EB-05, OP-16 e OP15-EB04 também dizem que a revisão indicada deve ser tratada como correta. A correção se aplica às versões paralelas quando o comunicado assim especifica.

### 4.2 Comunicados avulsos recentes conferidos

| Carta | Data e fonte | Antes → Depois | Motivo informado | Impacto normativo |
|---|---|---|---|---|
| `EB05-033 Nico Robin` | 23/10/2026 — [aviso EB-05](https://en.onepiece-cardgame.com/news/notice_eb05.html) | `O-Robi` → `Nico Robin` | Erro tipográfico no texto da carta. | Corrige a referência nominal: filtros, condições ou instruções que mencionem o nome devem reconhecer `Nico Robin`; o efeito em si não foi alterado. Alt-art incluída. |
| `OP16-081 Otama` | 29/05/2026 — [aviso OP-16](https://en.onepiece-cardgame.com/news/notice-op16.html) | `If you have a Character with a cost of 8 or more` → `If there is a Character with a cost of 8 or more` | Erro tipográfico no texto da carta. | Altera a condição de controle/posse para existência de Character. A FAQ OP16-081 confirma o caso em que somente o oponente tem Character com Cost 8 ou mais: a condição pode ser satisfeita. Alt-art incluída. |
| `OP15-023 Arlong` | 13/03/2026 — [aviso OP15-EB04](https://en.onepiece-cardgame.com/topics/notice_op15-023.php) | `Give up to 1 rested DON!! card from its owner's cost area...` → `Give up to 1 DON!! card from its owner's cost area...` | Erro tipográfico no texto da carta. | Retira a exigência `rested`: DON!! active ou rested podem ser selecionados. O FAQ OP15-023 esclarece que o ativador escolhe o DON!! na Cost Area do oponente, mas não pode transferir DON!! de um proprietário para Leader/Characters de outro proprietário. Alt-art incluída. |

### 4.3 Errata “Up to” de OP-01 — correção de escopo

O comunicado de 11/11/2022 não autoriza substituir mecanicamente toda ocorrência do número `1` por `up to 1`. Ele define grupos de texto e listas específicas de cartas afetadas. Entre os grupos expressamente listados estão:

- referências genéricas a `1` carta, com exceção expressa de todos os textos `[Draw 1 card]`;
- `you may trash 1 card` e `you may add 1 card` em cartas nominadas;
- `K.O. 1 of your opponent's` em cartas nominadas;
- `Your Leader or 1 of your Characters` e `Give your opponent's Leader or 1 of their Characters` em cartas nominadas;
- adicionar 1 carta do DON!! Deck, definir 1 carta como active, revelar 1 carta, restar 1 carta do oponente, colocar 1 carta no fundo do deck do proprietário e selecionar 1 carta, sempre nas cartas indicadas pelo comunicado.

A fonte oficial contém a lista de cartas afetadas para cada padrão, por exemplo `OP01-004 Usopp`, `OP01-011 Gordon`, `OP01-039 Killer`, `OP01-052 Raizo`, `OP01-078 Boa Hancock`, `OP01-080 Miss Doublefinger`, `OP01-095 Kyoshirou`, `OP01-064 Alvida`, `OP01-007 Caribou`, `OP01-017 Nico Robin`, `OP01-054 X.Drake`, `OP01-096 King`, `OP01-020 Hyogoro`, `OP01-026 Gum-Gum Fire-Fist Pistol Red Hawk`, `OP01-061 Kaido`, `OP01-003 Monkey.D.Luffy`, `OP01-016 Nami`, `OP01-033 Izo`, `OP01-063 Arlong` e `OP01-085 Mr.3`, entre outras. A enumeração acima é ilustrativa, não substitui as listas completas da fonte.

**Regra para o inventário:** cada carta afetada deve ser procurada na lista do comunicado; a exceção de `Draw 1 card` deve ser preservada. Não generalizar a mudança para outras coleções ou para cartas OP-01 que não constam nas listas.

Fonte: [“Up to” Card Errata [OP-01], 11/11/2022](https://en.onepiece-cardgame.com/rules/announcements/op01.php).

### 4.4 Exemplos históricos confirmados no hub central

| Carta | Data do hub | Antes → Depois | Motivo/natureza informada | Impacto normativo |
|---|---:|---|---|---|
| `OP07-097 Vegapunk` | 16/05/2025 | Insere `:` após `You may rest 1 of your DON!! cards`, separando custo e instrução. | Correção de redação/formatação. | A fronteira entre custo de ativação e texto do efeito deve ser interpretada conforme `After`; afeta a possibilidade de ativar e pagar a habilidade. |
| `OP09-058 Special Muggy Ball` | 13/12/2024 | `[Main] Return up to 1 of your opponent's Characters...` → `[Main] Your opponent chooses 1 of their Character...` | Correção registrada no hub; causa individual não especificada. | Altera quem escolhe a carta e remove a opcionalidade `up to` do número. A decisão é do oponente, mas a movimentação continua sendo a instrução da habilidade. |
| `OP06-034 Hyouzou` | 06/12/2024 | Tipo `Fish-Man` → `Merfolk`. | Correção de característica. | Muda resultados de filtros de tipo e condições de cartas. |
| `ST14-014 Gum-Gum Giant Rifle` | 16/08/2024 | `[Counter] If you have a Character...` → `If there is a Character...`; Trigger permanece. | Correção de redação. | Altera a condição de presença/controle, semelhante no tipo de questão a `OP16-081`, mas cada carta mantém seu próprio texto/FAQ. |
| `OP05-032 Pica` | 08/12/2023 | `you may rest up to 1 ... instead` → `you may rest 1 ... instead`. | Correção de redação no texto de substituição. | Muda a escolha do pagamento da substituição: ao escolher aplicá-la, a carta indicada deve ser restada; não pode escolher zero. |
| `OP03-054 Usopp's Rubber Band of Doom!!!` | 14/07/2023 | Trigger: `You may draw 1 card and trash 1...` → `Draw 1 card and you may trash 1...`. | Correção de opcionalidade. | Comprar passa a ser obrigatório; descartar do topo do deck continua opcional. A redação Counter da mesma carta também foi republicada no comunicado, mas não sofreu mudança semântica nessa linha. |
| `OP03-047 Zeff` | 14/07/2023 | `[On Play] You may return up to 1 Character..., and trash 2...` → `Return up to 1 Character..., and you may trash 2...`. | Correção de opcionalidade. | A instrução de retorno deixa de estar sob `You may`, mas `up to 1` ainda permite escolher zero; o trash de duas cartas passa a ser opcional. Não confundir a opcionalidade de escolher 0 com a opcionalidade de executar a instrução inteira. |

Fonte para todos os registros desta subseção: [Errata Cards — Bandai](https://en.onepiece-cardgame.com/rules/errata_card/).

### 4.5 Consequências e testes derivados

- `T-141`: `EB05-033` é reconhecida pelo nome corrigido em filtros de nome.
- `T-142`: a condição corrigida de `OP16-081` é satisfeita pela existência do Character descrito, inclusive no caso oficial em que o Character de Cost 8 ou mais pertence ao oponente.
- `T-143`: `OP15-023` pode selecionar DON!! active ou rested, mas não permite cruzar o DON!! entre proprietários.
- `T-144`: a correção `up to` de OP-01 somente se aplica às cartas e expressões listadas, respeitando exceções.
- `T-145`: uma carta com errata executa o texto `After`, não `Before`.
- `T-179`: a fronteira entre custo e efeito de `OP07-097` segue a pontuação/texto corrigido.
- `T-180`: `OP09-058` delega ao oponente a escolha de 1 Character conforme o texto corrigido.
- `T-181`: filtros de tipo consultam `Merfolk` para `OP06-034`, não o tipo antigo.
- `T-182`: `ST14-014` usa a condição corrigida `If there is...`.
- `T-183`: `OP05-032` não permite pagar a substituição restando zero Characters.
- `T-184`: Trigger de `OP03-054` compra obrigatoriamente 1 e deixa o trash do topo do deck opcional.
- `T-185`: `[On Play]` de `OP03-047` distingue retorno obrigatório/seleção `up to` do trash opcional.

### 4.6 Limitação da reconciliação histórica

A seção 4 contém as erratas recentes separadas verificadas e exemplos históricos confirmados diretamente no hub. Ela **não** afirma que todas as entradas da página oficial foram extraídas. O hub central contém registros anteriores e posteriores em uma lista extensa; além disso, há comunicados avulsos. A reconciliação integral por carta, inclusive paralelas e versões regionais, permanece em `Q-002`.

## 5. Regras fora do núcleo de gameplay

### 5.1 Procedimentos de torneio

**Registro:** `R-020`  
**Classificação:** `OUT_OF_SCOPE`  
**Fonte:** `SRC-TRM`, Tournament Rules Manual, 16/01/2026.

Procedimentos de torneio — inscrição, comunicação com judge, penalidades, conduta, organização e procedimentos próprios de evento — não são regras de resolução do jogo. Não precisam ser reproduzidos pelo motor de uma partida digital casual.

**Exceção:** uma regra de torneio que defina explicitamente como uma partida deve continuar ou ser encerrada pode ser implementada em camada específica de plataforma/torneio, sem ser confundida com a regra base de gameplay.

### 5.2 Raridade, ilustração e autoria

**Registro:** `R-021`  
**Classificação:** `OUT_OF_SCOPE`  
**Fonte:** `SRC-CR`, §§2-12 a 2-17.

Raridade, ilustração, ilustrador, crédito visual e metadados de impressão não alteram o estado nem a resolução de uma partida. Podem existir como metadados de coleção/apresentação, mas não são requisitos do motor de regras.

### 5.3 Banimentos, restrições e legalidade de formato

**Registro:** `R-022`  
**Classificação:** `OUT_OF_SCOPE`.

A lista oficial de 12/10/2026 trata de cartas banidas, restritas e combinações proibidas. Ela afeta a legalidade de um deck em determinado formato, não o efeito de uma carta durante a partida. A eventual validação de deck/formato é um escopo próprio, separado do motor de gameplay.

A lógica de gameplay não deve codificar a lista de banimentos como comportamento de carta.

### 5.4 Idioma de cartas e eventos presenciais/remotos

**Registro:** `R-023`  
**Classificação:** `OUT_OF_SCOPE` para as regras de partida; pode ser dado de configuração de torneio.

Regras regionais sobre idiomas permitidos e ajustes de procedimentos em eventos remotos não devem alterar automaticamente o motor de gameplay. O FAQ geral inclui uma adaptação operacional para torneios por webcam ao olhar a Life do oponente; isso é uma regra de evento online, não a regra base de informação/zonas de uma partida digital.

---

## 6. OPEN_QUESTION — Questões em aberto

Os itens abaixo registram limitações reais da auditoria. Casos específicos respondidos por FAQ foram fechados somente dentro de seu escopo e não eliminam a necessidade de investigar outras cartas com redação semelhante.

| ID | Questão | Por que importa | Próxima verificação |
|---|---|---|---|
| `Q-001` | FAQs antigos e ainda não examinados individualmente | O índice oficial lista documentos anteriores e há perguntas que podem esclarecer interações não cobertas pelos sets recentes. | Auditar ST-30, OP-13, OP-12, ST-29, PRB02, ST22–ST28, OP11, EB02, ST21, OP10, OP07 e os documentos mais antigos pertinentes ao catálogo de cartas. |
| `Q-002` | Reconciliação integral do hub de erratas com o catálogo de cartas | A página central é extensa e comunicados avulsos também existem; uma alteração de texto pode mudar condição, alvo, quantidade, custo, tipo ou opcionalidade. | Enumerar todas as entradas do hub e comunicados de set, registrar `Before`/`After`, data, versões paralelas e cruzar com todas as cartas suportadas. |
| `Q-003` | Substituições múltiplas e encadeadas fora dos casos já respondidos | O CR fixa prioridade e permite que uma substituição habilite outra, mas a aplicação concreta depende da situação e da redação. | Acrescentar cenários com duas substituições do mesmo jogador, substituições de jogadores diferentes e substituições que alteram condições uma da outra. |
| `Q-004` | Limite de substituição coletiva | FAQs recentes mostram custos únicos capazes de impedir saídas simultâneas de várias cartas, mas não há uma regra genérica única para qualquer grupo. | Continuar auditando todas as perguntas `instead` e separar evento coletivo de remoções individuais múltiplas. |
| `Q-005` | Semântica completa de escolha e execução por jogadores diferentes | Escolher uma opção, escolher uma carta, mover a carta e ser seu proprietário são papéis distintos. | Terminar extração dos FAQs com `opponent chooses`, escolhas de mãos secretas, ações feitas pelo oponente e movimentos para decks/Life/Trash. |
| `Q-006` | Condições avaliadas em momentos diferentes | Custos, trechos precedentes e efeitos contínuos podem alterar o estado observado por partes posteriores. | Auditar mais textos que combinem `if`, `then`, `if you do`, custo, busca, alteração de DON!! e filtros de Cost/Power. |
| `Q-007` | Dano, Double Attack, Trigger e derrota com substituições encadeadas | O CR determina dano em um ponto definido; Trigger suspende dano; FAQ EB05-052 trata de substituição com 0 Life. A composição merece casos extras. | Verificar múltiplas substituições de dano, Trigger que altera a condição, zero Life e efeitos de vitória/derrota, mantendo o FAQ específico separado da regra geral. |
| `Q-008` | Efeitos que se aplicam em áreas secretas | O CR reconhece efeitos `according to/under the rules` válidos em áreas secretas, mas não toda redação alcança essas áreas. | Auditar cartas com consultas a Deck/Hand/Life e efeitos contínuos que definem nome, tipo ou atributo. |
| `Q-009` | Deck construction alterada por efeitos | O CR permite exceções de construção de deck e efeitos específicos. | Inventariar todas as cartas que modificam limites, categorias, cores ou números de cópias, caso a validação de deck faça parte do produto. |
| `Q-010` | Loops e procedimentos repetitivos em cartas concretas | O CR define os procedimentos para loops, mas a identificação dos ciclos e as escolhas em cada caso podem exigir análise mais profunda. | Buscar FAQs e textos oficiais com repetições obrigatórias/opcionais e testar estados completos repetidos. |
| `Q-011` | Diferenças entre idiomas/edições após errata | A documentação inglesa consultada pode não conter todas as variantes de impressão ou comunicados regionais. | Comparar fontes oficiais regionais somente quando um conflito concreto for encontrado; registrar o idioma, região e documento que sustenta a conclusão. |
| `Q-012` | Escopo temporal de efeitos contínuos e modificações de características | Alguns FAQs confirmam snapshots; outros estabelecem duração contínua ou dependência da presença em campo. | Não generalizar resultados de OP17-097/OP14-098. Buscar mais exemplos de efeitos que entram/saiam do campo e mudam a característica de cartas futuras. |
| `Q-013` | Procedimento exato de derrota no caso EB05-052 e zero Life | CR §7-1-4-1-1-1 descreve a vitória quando o dano é determinado com 0 Life; EB05-052 responde que uma substituição de dano pode impedir a derrota no cenário perguntado. | Manter teste de Gloriosa específico e buscar outros FAQs que precisem explicar a ordem entre determinação, substituição, dano múltiplo e julgamento. |

## 7. Matriz de cobertura da documentação

Os estados descrevem a investigação documental desta versão, não a validade de uma implementação. `FULL_TEXT_PASS` significa que o conteúdo do documento foi percorrido; não significa que todos os possíveis efeitos de todas as cartas foram provados por testes.

| Domínio/documento | O que foi efetivamente examinado nesta versão | Estado |
|---|---|---|
| Comprehensive Rules v1.2.1 — §§1–6 | Leitura do conteúdo geral já refletido em R-001 a R-011; pontos de preparação, área, custo e fases preservados, com correções pontuais. | `REVIEWED` |
| §7 Card Attacks and Battles | Estrutura da batalha e FAQs sobre ataque, Blocker, Counter, dano e persistência de alvo; interações completas ainda não enumeradas. | `PARTIAL` |
| §8 Activating and Resolving Effects | Categorias, Replacement Effects, prioridade, ativação, custo, escolha, `if`/`then`, efeitos contínuos e casos adicionais dos FAQs. | `PARTIAL` |
| §9 Rule Processing | Regra geral de derrota e timing; FAQ específico de OP15-022 Brook incluído sem generalização. | `REVIEWED_WITH_CARD_CASES` |
| §10 Keywords | Keywords principais e timings; casos de Counter, Trigger, Blocker, On K.O. e Double Attack complementados. | `PARTIAL` |
| §11 Other | Loops, revelação e consulta de áreas secretas; casuística adicional de loop permanece pendente. | `REVIEWED` |
| FAQ geral (28/02/2025) | Documento de 10 páginas percorrido; esclarecimentos de Power/Cost, DON!!, zonas, batalha, Trigger e escolhas incorporados. | `FULL_TEXT_PASS` |
| FAQ EB-05 (06/11/2026) | Documento recente percorrido para perguntas relevantes; EB05-034/046/052/061 e demais casos extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQ OP-17 (30/10/2026) | Documento de 10 páginas percorrido; substituições coletivas, timing, custo, escolha, Cost e Counter extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQ OP-16 (30/10/2026) | Documento de 9 páginas percorrido; Power exato, `if`/`then`, movimentação da origem, DON!! e seleção extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQ ST-34 (21/08/2026) | Página única examinada integralmente. | `FULL_TEXT_PASS` |
| FAQs ST-36/ST-35/ST-33/ST-32/ST-31 (17/07/2026) | Páginas únicas examinadas integralmente; custos de mão, condições de turno, contagem agregada de DON!!, e escolhas em mão incorporados nos registros pertinentes. | `FULL_TEXT_PASS` |
| FAQ OP14-EB04 (26/06/2026) | Documento de 10 páginas percorrido; Trigger, movimento de zona, Cost, base Power, escolhas e sequências extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQ ST-30 (29/05/2026) | Presença e data identificadas no índice oficial; conteúdo não examinado. | `IDENTIFIED_NOT_REVIEWED` |
| FAQ OP15-EB04 (13/03/2026) | Documento de 15 páginas percorrido; substituições, deck vazio, DON!!, Life e condições extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQ EB-03 (06/02/2026) | Documento de 4 páginas percorrido; Power negativo, condições e ações extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQ Promotion Cards (06/02/2026) | Documento de 6 páginas percorrido; ataque declarado, Blocker, substituições e vitória extraídos. | `PARTIAL_CONTENT_AUDIT` |
| FAQs OP-13/OP-12/ST-29 e anteriores | Identificados no índice oficial; não auditados individualmente. | `NOT_REVIEWED` |
| Hub central de erratas | Regra de precedência e registros funcionais selecionados (OP07-097, OP09-058, OP06-034, ST14-014, OP05-032, OP03-054/047) conferidos. | `PARTIAL` |
| Erratas EB05-033/OP16-081/OP15-023 | Comunicados individuais e textos `Before`/`After` conferidos. | `REVIEWED` |
| Errata “Up to” OP-01 | Comunicado individual, grupos de texto, exceções e exemplos de cartas conferidos; não foi cruzado com o catálogo integral de cartas do simulador. | `REVIEWED_WITH_CATALOG_GAP` |
| Tournament Rules Manual | Usado apenas para delimitar regras de evento fora do núcleo de gameplay. | `REVIEWED_FOR_SCOPE` |
| Banlist/restricted list | Identificada como legalidade de formato, não como resolução de gameplay. | `IDENTIFIED` |

### 7.1 Avaliação geral

A v0.3 aumenta a cobertura de FAQs e corrige conclusões que poderiam ser excessivamente generalizadas. Há agora registros específicos de substituição parcial, eventos simultâneos, cláusulas `if`/`then`, escolha e propriedade, origem do Trigger, alvo de Blocker e escopo temporal de efeitos de características.

**A Fase 1 continua aberta.** O Comprehensive Rules geral está organizado em todos os onze capítulos, mas os FAQs de todos os sets não foram auditados. O hub de erratas também não foi reconciliado linha a linha com o catálogo completo de cartas. Os registros `REVIEWED` não representam uma certificação de conformidade de cartas ou de motor.

## 8. Cenários de teste derivados: índice

Os cenários `T-001` a `T-185` foram colocados junto aos registros para preservar a relação entre norma e comportamento verificável. Esta lista é um índice documental de planejamento, não uma suíte implementada. Os testes `T-179` a `T-185` complementam a seção de erratas; `T-149` a `T-178` foram adicionados nos registros R-024 a R-030. São `IMPLEMENTATION_NOTE`, não afirmações normativas independentes.

Os cenários precisam ser transformados em testes somente depois que os casos de borda e as interações relevantes forem cobertos. Ao implementá-los, não usar o código anterior como oráculo: o resultado esperado deve derivar da regra e da fonte citada.

Uma mesma regra pode exigir:
- teste unitário do procedimento;
- teste de interação entre efeitos;
- teste de estado de zonas e visibilidade;
- teste de regressão para uma interação esclarecida por FAQ;
- teste de sequência/timing com dois jogadores.

---

## 9. Critério para considerar uma regra suficientemente inventariada

Um registro só deverá ser considerado fechado quando:

1. possui referência oficial precisa (seção/regra e página ou FAQ/carta/pergunta);
2. descreve condição, momento, ordem, consequência e exceção relevantes;
3. distingue texto expresso de interpretação;
4. possui pelo menos um cenário de teste quando o comportamento for implementável;
5. não depende de uma conclusão tirada apenas de wiki, simulador, discussão comunitária ou implementação antiga;
6. informa explicitamente qualquer dependência ou dúvida ainda aberta.

A ausência de uma pergunta em FAQ não significa que a interação seja impossível ou não exista. Significa apenas que não foi encontrada uma resposta específica no conjunto documental consultado até o momento.

---

## 10. Registro de alterações em relação ao v0.2

| Área | Alteração na v0.3 | Motivo/fonte |
|---|---|---|
| Cabeçalho e status | Versão atualizada para 0.3; Fase 1 permanece `IN_PROGRESS`. | A auditoria ampliou o inventário, mas não esgotou FAQs/erratas. |
| Fontes FAQ | Confirmada a data do FAQ EB-05 como 06/11/2026 (o v0.2 já estava correto); adicionados ST-35/ST-33/ST-32/ST-31, OP14-EB04, OP15-EB04, EB-03 e Promotion Cards; demais documentos foram listados como não auditados. | Índice oficial de FAQ e PDFs individuais. |
| Hierarquia normativa | Removida a apresentação excessivamente rígida do FAQ específico como nível universal de precedência sobre o CR; a resposta passa a ser delimitada ao caso perguntado. | A errata prevalece sobre a redação original da carta; CR estabelece regras gerais; FAQs esclarecem situações concretas. |
| R-013 | Classificação alterada de `CARD_EFFECT_CAPABILITY` para `BOTH`; corrigida a prioridade de `8-1-3-4-2`; acrescentada `8-1-3-4-4-1`. | Comprehensive Rules v1.2.1, §§8-1-3-4-2 e 8-1-3-4-4-1. |
| R-014/R-015/R-025/R-026 | Ampliados os casos de custo impossível, DON!! total no campo, escolhas executadas pelo oponente, timing “becomes rested”, ativação de Event e independência de ações. | CR §§4-10 e 8-4; FAQs ST-33/ST-35/ST-36, EB-03 e OP14-EB04. |
| R-004/R-015/R-016/R-017 | Esclarecidas mudanças de zona após ativação, janela de efeitos de ataque, caso OP15-022 Brook e estado de origem do Trigger; R-017 alterado para `BOTH`. | CR §§3-1-6, 8-1-3-1-3, 8-6, 9 e 10-1-5-3; FAQs. |
| R-012/R-028 | Acrescentados alcance agregado da expressão de DON!! dada, restrições de ataque no primeiro turno, Rush, Blocker e persistência de ataque/alvo após alteração de DON!!. | OP17, ST31/ST32, OP14-EB04 e Promotion Cards FAQs. |
| R-024 a R-030 | Novos registros sobre substituição parcial/coletiva, `if`/`then`, papéis de escolha/propriedade, Trigger/origem, alvo/Blocker, duração e Counter. | CR e FAQs de OP17, OP16, OP15, OP14, ST31, ST34, ST36 e Promo. |
| Seção 4 — Erratas | OP-01 foi reclassificada como comunicado de escopo fechado por padrões e listas, não regra universal; adicionados impactos detalhados de EB05-033, OP16-081, OP15-023 e exemplos históricos do hub. | Comunicados oficiais da Bandai e Errata Cards. |
| Seção 6 — Questões | Atualizadas para refletir o que foi respondido e o que segue realmente pendente. | Auditoria documental v0.3. |
| Seção 7 — Cobertura | Separados os estados de texto integralmente percorrido, auditoria temática parcial e documento apenas identificado. | Limitação real do trabalho realizado. |
| Testes | Índice passa a considerar `T-001` a `T-185`; novos casos detalham substituições, resolução parcial, seleção, Trigger, Blocker, duração e Counter. | Regras e FAQs citados junto aos cenários. |

### 10.1 Correções explícitas de conclusões do v0.2

1. **FAQ EB-05:** a data de 06/11/2026 do v0.2 foi confirmada no índice oficial; não alterar essa data para 30/10/2026.
2. **OP-01:** não aplicar `up to 1` a toda ocorrência de “1”; verificar o padrão de redação e a lista de cartas afetadas, respeitando a exceção de `[Draw 1 card]`.
3. **Replacement effects:** não descrever prioridade como se fosse a substituição do efeito que criou a situação; o texto de `8-1-3-4-2` refere-se à substituição da carta que gerou aquele efeito de substituição.
4. **Replacement no meio do efeito:** o texto restante continua em ordem após aplicar a substituição (`8-1-3-4-4-1`); uma substituição não cancela automaticamente o restante da habilidade.
5. **`if` versus `then`:** não agrupar ambos como uma única condição de continuidade. `4-10-1` e `4-10-2` dão consequências diferentes.
6. **Escolha/propriedade:** o jogador que ativa, escolhe, realiza a ação e é proprietário da carta podem ser papéis distintos; a regra-padrão `8-4-4-3` não apaga instruções específicas.
7. **Classificação:** regras gerais de substituição e keywords precisam ser processadas pelo motor e também são utilizadas por efeitos, portanto os registros R-013 e R-017 passam a `BOTH`.

---

## 11. Próxima parte da investigação

A próxima continuação permanece na Fase 1 e deve priorizar:

1. leitura de ST-30, OP-13, OP-12, ST-29, PRB02, ST22–ST28, OP11, EB02, ST21, OP10, OP07 e FAQs mais antigos relevantes ao catálogo;
2. extração completa e cruzamento da lista histórica de erratas com cada carta disponível no simulador, incluindo comunicados avulsos;
3. novos casos de substituições concorrentes/encadeadas e de dano em zero Life, sem generalizar FAQ de carta;
4. auditoria das interações de características em áreas secretas e efeitos de construção de deck;
5. revisão cruzada final de cada cenário T-001–T-185 para retirar duplicações, fechar condições iniciais e garantir uma fonte normativa por resultado esperado.

A Fase 1 só poderá ser considerada concluída quando os documentos relevantes tiverem sido examinados, as questões normativas relevantes estiverem resolvidas ou explicitamente delimitadas, e a matriz de cobertura não depender de documentos apenas identificados. A linguagem das cartas, a VM e a arquitetura continuam fora desta etapa.
