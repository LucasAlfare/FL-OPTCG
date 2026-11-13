# FL_CARD_LANGUAGE_ABSTRACT_MODEL_v1.0_STABLE.md

**Arquivo:** `FL_CARD_LANGUAGE_ABSTRACT_MODEL_v1.0_STABLE.md`  
**Versão:** 1.0 — modelo abstrato estabilizado  
**Data:** 8 de novembro de 2026  
**Projeto:** FL-OPTCG  
**Status:** `STABLE — GRAMMAR-READY`  
**Substitui:** `FL_CARD_LANGUAGE_ABSTRACT_MODEL_v0.3.md`  
**Base normativa:** `GAME_RULES_INVENTORY_v0.3.md`; `FL_CARD_LANGUAGE_SEMANTICS.md`; `FL_CARD_LANGUAGE_MODEL_VALIDATION_v0.1.md`; Comprehensive Rules v1.2.1.

---

## 1. Objetivo e escopo

Este documento define **todos os elementos que podem aparecer em um programa de carta** e **como eles se relacionam**, de forma completa o suficiente para que a gramática e a sintaxe textual sejam construídas sem decisões semânticas pendentes.

O documento é agnóstico de codificação. Não contém sintaxe, pseudo-código, serialização, TOML, opcodes, bytecode, estruturas de dados concretas ou nomes de tipos de linguagem de programação. Contém apenas **definições conceituais** e **catálogos fechados** para esta versão.

Os catálogos apresentados nas seções 11, 17, 18, 19 e 20 são **fechados nesta versão**. Qualquer extensão futura exige revisão formal deste documento e justificativa normativa. Não há reticências, elipses ou itens omitidos nas listas.

---

## 2. Princípios

1. **Carta é programa.** Cada carta é descrita por identidade, metadados, referências de texto vigente e habilidades declarativas.
2. **Categoria e resultado são eixos diferentes.** Auto, Activate, Permanent e Replacement classificam habilidades. Efeito pontual e efeito contínuo descrevem a natureza do que elas produzem.
3. **Custo não é resolução.** Custo e corpo ficam em posições semânticas distintas, embora possam reutilizar os mesmos tipos de operação.
4. **Escolha não é seleção.** Escolher uma opção de execução, selecionar objetos e decidir se executa ou paga são construções diferentes.
5. **Referências têm significado tipado.** Jogador, controlador, proprietário, origem, escolhedor e executor não são intercambiáveis.
6. **Predicados não alteram o estado.** Condições e filtros consultam o estado ou o histórico; operações produzem mudanças.
7. **A ordem importa.** Operações e pagamentos são ordenados quando as regras ou o texto exigem resolução sequencial.
8. **Catálogos são do jogo, não da carta.** Uma carta usa operações gerais suportadas; não pode introduzir uma instrução exclusiva para si.
9. **Sem aproximação silenciosa.** Comportamento não suportado ou não esclarecido deve ser identificado, nunca convertido em interpretação inventada.
10. **Ciclo de vida da origem é explícito.** Uma habilidade pode ter zonas distintas de ativação e resolução, ou nenhuma zona durante a resolução.
11. **Efeitos contínuos podem atingir regras, não apenas características.** Proibições, negações e modificações de procedimentos são formas de efeito contínuo.
12. **Substituições podem interceptar situações coletivas.** A situação substituída e o custo não escalam necessariamente com o número de objetos afetados.
13. **Nenhuma estrutura está ausente.** Toda estrutura necessária à representação das cartas exercitadas está definida neste documento. Casos normativos ainda não esclarecidos são registrados como pendências normativas no inventário, mas o modelo possui estrutura para representá-los.

---

## 3. Programa de carta

Um programa de carta é composto por quatro partes: identidade, metadados, fonte e habilidades.

**Identidade** contém as características impressas da carta: número da carta, nome, categoria, cores, tipos, atributos, custo, power, life e counter. Custo, power, life e counter podem estar ausentes quando não se aplicam à categoria. A identidade é usada para consultas de filtro, comparação e reconhecimento.

**Metadados** contêm raridade, coleção, ilustrador, texto de sabor e informações editoriais. Não participam de nenhuma decisão de jogo, salvo quando uma regra expressamente lhes atribuir significado.

**Fonte** contém o texto oficial vigente, referências a regras do Comprehensive Rules, referências a FAQs e referência a errata quando aplicável. Quando há errata, o texto oficial vigente é o texto posterior à correção. O motor não interpreta texto natural; a fonte serve à rastreabilidade e à auditoria.

**Habilidades** contêm todas as habilidades declaradas pela carta, incluindo restrições de construção de deck. Uma carta pode ter zero, uma ou várias habilidades. Cada habilidade é independente das demais, salvo quando o texto expressamente as vincula.

---

## 4. Habilidades

Toda habilidade pertence a exatamente uma das quatro categorias: Auto, Activate, Permanent ou Replacement.

### 4.1 AutoAbility

Uma habilidade automática é ativada por um evento ou por uma janela de ativação. Ela possui os seguintes componentes:

- **Evento**: o padrão de evento que habilita a ativação. Pode ser um evento simples, uma disjunção de eventos ou uma conjunção de eventos.
- **Janela**: usada quando o evento sozinho não determina a janela. Exemplos: janela de Trigger de dano de Life, janela de início de jogo.
- **Zona de ativação**: a zona da carta-fonte no momento em que o evento ocorre. Para On K.O., é a área de campo. Para Trigger, é nenhuma zona.
- **Zona de resolução**: a zona em que o efeito resolve. Para On K.O., é a lixeira. Para Trigger, é nenhuma zona.
- **Zona pós-resolução**: a zona para onde a carta vai após a resolução, quando diferente da zona de resolução. Para Trigger, o padrão é a lixeira.
- **Ciclo de vida da zona**: indica se a ativação e a resolução ocorrem na mesma zona, se a carta muda de zona entre ativação e resolução, ou se a carta não pertence a nenhuma zona durante a resolução.
- **Condições**: lista de condições adicionais que precisam ser satisfeitas.
- **Custo**: custo de ativação, quando existir. Auto pode ter custo.
- **Limite de uso**: restrição de frequência, quando aplicável.
- **Resolução**: sequência ordenada de passos.

O mapeamento de On K.O. é: evento On K.O., zona de ativação campo, zona de resolução lixeira, ciclo de vida com mudança para a zona de resolução.

O mapeamento de Trigger é: janela de Trigger de dano de Life, zona de ativação nenhuma, zona de resolução nenhuma, zona pós-resolução lixeira, ciclo de vida sem zona durante a resolução.

### 4.2 ActivateAbility

Uma habilidade ativável é declarada pelo jogador dentro de uma janela permitida. Ela possui os seguintes componentes:

- **Janela**: a janela em que a habilidade pode ser declarada.
- **Zona de origem**: a zona de onde a habilidade é ativada. Normalmente campo para efeitos de campo e mão para Event cards.
- **Condições**: lista de condições adicionais.
- **Custo**: custo de ativação, quando existir.
- **Limite de uso**: restrição de frequência, quando aplicável.
- **Resolução**: sequência ordenada de passos.

Uma habilidade ativável pode não ter custo. A presença de custo é determinada pelo texto e pelas regras, não pela categoria isoladamente.

### 4.3 PermanentAbility

Uma habilidade permanente declara um efeito que afeta continuamente o jogo enquanto for válido. Ela possui os seguintes componentes:

- **Escopo**: jogo ou construção de deck. O escopo de jogo indica que o efeito atua durante a partida. O escopo de construção de deck indica que o efeito atua na validação pré-partida.
- **Zonas afetadas**: a zona ou o conjunto de zonas em que o efeito se aplica. Pode incluir mão, deck e life quando o texto e as regras o permitirem.
- **Condição de validade**: condição contínua que determina se o efeito está ativo.
- **Efeito contínuo**: o efeito concreto aplicado (seção 12).
- **Duração**: quando a permanência não é apenas enquanto válido.

Uma habilidade permanente não é modelada como sequência de ações pontuais. Se o texto contiver comportamentos de categorias diferentes, eles devem ser representados como habilidades distintas.

### 4.4 ReplacementAbility

Uma habilidade de substituição intercepta uma situação e a substitui por outra. Ela possui os seguintes componentes:

- **Situação interceptada**: o padrão da situação que pode ser interceptada (seção 13).
- **Escopo da situação**: individual ou coletivo. Individual indica que a substituição intercepta um processamento individual. Coletivo indica que intercepta um evento que afeta um conjunto de objetos simultaneamente; o custo é avaliado uma vez pelo evento, independentemente do número de objetos salvos.
- **Condições**: condições adicionais para a substituição.
- **Procedimento substituto**: o que acontece em vez da situação.
- **Custo de ativação**: custo do procedimento substituto, distinto da situação substituída.
- **Escolha**: decisão de aplicar ou não a substituição, quando opcional.
- **Limite de uso**: restrições de uso, quando aplicável.

### 4.5 Distinções preservadas

Auto e Activate compartilham condições, custo, limite de uso e resolução, mas diferem no mecanismo de ativação e no ciclo de vida. Permanent e Replacement têm estruturas próprias. A implementação abstrata pode reutilizar componentes internos comuns, mas as diferenças semânticas entre as categorias precisam ser preservadas.

---

## 5. Eventos e janelas

### 5.1 Catálogo de eventos

O catálogo de eventos é fechado nesta versão. Os eventos são:

1. On Play: a carta é jogada e entra em campo.
2. On K.O.: a carta é K.O.da.
3. When Attacking: a carta declara ataque.
4. On Opponent Attack: o oponente declara ataque.
5. On Block: a carta é usada como Blocker.
6. On Damage Taken: o jogador ou a carta recebe dano.
7. On Damage Dealt: o jogador ou a carta causa dano.
8. Start of Turn: o turno começa.
9. End of Turn: o turno termina.
10. Start of Main Phase: a fase principal começa.
11. Start of Game: a partida começa.
12. On Character Rested: um Character é restado.
13. On Card Moved: uma carta muda de zona.
14. On Life Changed: a quantidade de cartas de Life muda.
15. On DON Given: um DON é dado a uma carta.
16. On DON Removed: um DON é removido de uma carta.
17. On Card Drawn: uma carta é comprada.
18. On Card Trashed: uma carta é enviada à lixeira.
19. On Card Played: uma carta é jogada.
20. On Effect Activated: um efeito é ativado.
21. On Counter Used: um Counter é usado.
22. On Battle Start: uma batalha começa.
23. On Battle End: uma batalha termina.
24. On Refresh Phase: a fase de refresh começa.
25. On DON Phase: a fase de DON começa.
26. On Draw Phase: a fase de compra começa.
27. On End Phase: a fase final começa.

### 5.2 Catálogo de janelas de ativação

O catálogo de janelas é fechado nesta versão. As janelas são:

1. Main Phase.
2. Counter Step.
3. Block Step.
4. Attack Step.
5. Damage Step.
6. Life Damage Trigger.
7. Start of Game.
8. End Phase.
9. Refresh Phase.
10. DON Phase.
11. Draw Phase.
12. Main Phase Start.
13. Battle Start.
14. Battle End.

### 5.3 Padrões de evento

Um padrão de evento pode ser um evento simples, uma disjunção de eventos ou uma conjunção de eventos. A disjunção indica que qualquer um dos eventos habilita a ativação. A conjunção indica que mais de um evento precisa ocorrer no mesmo contexto. A conjunção é usada quando o texto expressamente exige mais de um evento.

---

## 6. Zonas e ciclo de vida

### 6.1 Catálogo de zonas

O catálogo de zonas é fechado nesta versão. As zonas são:

1. Deck.
2. DON Deck.
3. Hand.
4. Trash.
5. Leader Area.
6. Character Area.
7. Stage Area.
8. Cost Area.
9. Life Area.
10. Nenhuma zona.

### 6.2 Ciclo de vida

O ciclo de vida da zona indica como a ativação e a resolução se relacionam com as zonas da carta-fonte. Os valores são:

1. **Mesma zona**: ativação e resolução ocorrem na mesma zona. A zona pós-resolução não se aplica.
2. **Mudança para a zona de resolução**: a carta muda de zona entre ativação e resolução. A zona pós-resolução não se aplica, pois a carta já está na zona de resolução.
3. **Nenhuma zona durante a resolução**: a carta não pertence a nenhuma zona durante a resolução. A zona pós-resolução define para onde a carta vai após a resolução. O padrão para Trigger é a lixeira.

---

## 7. Condições

### 7.1 Tipos de condição

Uma condição pode ser de cinco tipos:

1. **Condição estática**: avalia o estado atual no momento determinado pelas regras. É composta por um predicado de estado.
2. **Condição histórica**: avalia eventos ocorridos em uma janela temporal. É composta por tipo de evento, sujeito e janela temporal.
3. **Condição existencial**: quantifica sobre um conjunto de objetos. É composta por escopo de zona e predicado de estado.
4. **Condição composta**: combina condições com operadores de conjunção, disjunção ou negação.
5. **Condição de dependência**: satisfeita se e somente se a ação referenciada foi executada com sucesso em sua totalidade. Se a ação foi parcialmente impossível, a condição não é satisfeita.

### 7.2 Catálogo de predicados de estado

O catálogo de predicados de estado é fechado nesta versão. Os predicados são:

1. Tem tipo especificado.
2. Tem atributo especificado.
3. Tem cor especificada.
4. Tem categoria especificada.
5. Comparação de power efetivo com operador e valor.
6. Comparação de base power com operador e valor.
7. Comparação de custo efetivo com operador e valor.
8. Comparação de base cost com operador e valor.
9. Está rested.
10. Está active.
11. Tem keyword especificada.
12. Comparação de quantidade de DON dados à própria carta com operador e valor.
13. Comparação de quantidade total de DON dados no campo com operador e valor.
14. É controlado por papel especificado.
15. É propriedade de papel especificado.
16. Está na zona especificada.
17. Tem nome especificado.
18. Tem life especificado.
19. Tem counter especificado.
20. Foi jogado neste turno.
21. Foi jogado nesta batalha.
22. Foi K.O. neste turno.
23. Foi movido de zona neste turno.

### 7.3 Condições históricas

Uma condição histórica é composta por tipo de evento, sujeito e janela temporal. A janela temporal pode ser este turno, esta batalha, esta fase ou esta partida. O sujeito pode ser a própria carta, outra carta, um jogador ou um conjunto de objetos.

---

## 8. Seleções e escolhas

### 8.1 Seleção

Uma seleção identifica um ou mais objetos do jogo segundo escopo, filtro, quantidade, papel do escolhedor, política de informação e vínculo. Os componentes são:

- **Escopo**: a zona ou o conjunto de zonas consultadas.
- **Filtro**: o predicado de elegibilidade.
- **Quantidade**: exata, até um limite, pelo menos um limite, entre dois limites, todos ou qualquer um.
- **Papel do escolhedor**: quem escolhe.
- **Política de informação**: público, olhar oculto, oculto apenas para o proprietário ou revelar.
- **Vínculo**: referência que permite usar os objetos selecionados em operações posteriores.

### 8.2 Escolha entre opções

Uma escolha entre opções determina qual alternativa será executada. Os componentes são:

- **Papel do escolhedor**: quem decide a opção.
- **Papel do executor**: quem executa a consequência da opção escolhida.
- **Momento da escolha**: na ativação, na resolução ou antes de um passo da resolução.
- **Opções**: lista de alternativas. Cada opção pode ser uma resolução ou um caminho de custo.
- **Opcionalidade**: se a escolha pode ser recusada.

Os papéis do escolhedor e do executor são distintos e não podem ser fundidos. O oponente pode escolher uma opção cujo executor é o ativador.

### 8.3 Papéis de jogador

O catálogo de papéis de jogador é fechado nesta versão. Os papéis são:

1. **Próprio**: o jogador que controla a carta que possui a habilidade no contexto da regra.
2. **Oponente**: o oponente do jogador que controla a carta no contexto da regra.
3. **Jogador do turno**: o jogador que está executando o turno.
4. **Não-turno**: o oponente do jogador do turno.
5. **Ativador**: o jogador que ativou a habilidade.
6. **Escolhedor**: o jogador que faz uma escolha.
7. **Executor**: o jogador que executa a consequência.
8. **Proprietário**: o proprietário original de uma carta ou objeto.
9. **Controlador**: o controlador atual de uma carta ou objeto.
10. **Afetado**: o jogador alvo de um efeito.

O próprio e o ativador podem coincidir na maioria dos casos, mas são distintos quando uma habilidade é copiada ou redirecionada. O proprietário e o controlador são distintos conforme a regra geral. O escolhedor e o executor são distintos.

---

## 9. Custos

Um custo é composto por uma sequência de pagamentos, opcionalidade, caminhos alternativos e custo adicional. Os componentes são:

- **Pagamentos**: sequência ordenada de passos de pagamento.
- **Opcionalidade**: se o custo pode ser recusado.
- **Caminhos alternativos**: caminhos de pagamento alternativos, quando o texto oferece alternativas.
- **Custo adicional**: custo adicional imposto por outro efeito, processado após o custo base.

Cada passo de pagamento é composto por operação, papel do escolhedor, papel do executor e opcionalidade.

Ações de pagamento e ações de resolução usam o mesmo catálogo de operações, mas o contexto determina o papel semântico. Uma operação de descarte pode ser custo ou efeito; o contexto decide.

---

## 10. Resolução

Uma resolução é uma sequência ordenada de passos. Cada passo pode ser uma operação, uma seleção, uma escolha, um bloco condicional, um bloco de repetição ou uma criação de efeito.

### 10.1 Operação

Uma operação é composta por tipo de operação, parâmetros, opcionalidade e papel do executor. O catálogo de tipos de operação está na seção 11.

### 10.2 Bloco condicional

Um bloco condicional é composto por condição, ramo então, ramo senão e tipo de dependência. Os tipos de dependência são:

1. **Se**: a condição bloqueia o bloco; se falsa, o ramo então não resolve.
2. **Então**: sequência independente; a falha da ação anterior não impede o bloco.
3. **Se você fizer**: depende da condição de dependência da seção 7.1.
4. **Independente**: ações sem dependência.

### 10.3 Repetição

Um bloco de repetição é composto por quantidade de repetições, corpo e condição de parada antecipada. A quantidade pode ser exata, até um limite ou calculada.

### 10.4 Criação de efeito

Uma criação de efeito é composta por efeito contínuo e duração. É usada quando uma habilidade Auto ou Activate cria um efeito contínuo como parte da resolução.

---

## 11. Catálogo de operações

O catálogo de operações é fechado nesta versão. As operações são:

1. **Comprar**: mover a carta do topo do deck para a mão.
2. **Descartar da mão**: mover uma carta da mão para a lixeira.
3. **Descartar do deck**: mover uma carta do deck para a lixeira.
4. **K.O.**: mover um Character da área de Character para a lixeira do proprietário, gerando evento de K.O.
5. **Mover**: mover uma carta de uma zona para outra.
6. **Restar**: colocar uma carta no estado rested.
7. **Tornar active**: colocar uma carta no estado active.
8. **Dar DON**: atribuir um DON a uma carta.
9. **Adicionar DON**: adicionar um DON à área de custo ou ao DON deck.
10. **Remover DON**: remover um DON de uma carta ou área.
11. **Jogar da mão**: jogar uma carta da mão.
12. **Jogar da lixeira**: jogar uma carta da lixeira.
13. **Jogar do deck**: jogar uma carta do deck.
14. **Revelar**: tornar uma carta visível.
15. **Olhar**: consultar cartas de uma zona secreta sem movê-las.
16. **Embaralhar**: alterar aleatoriamente a ordem de uma zona.
17. **Modificar power**: alterar o power efetivo de uma carta.
18. **Modificar custo**: alterar o custo efetivo de uma carta.
19. **Definir base power**: alterar o base power de uma carta.
20. **Definir base cost**: alterar o base cost de uma carta.
21. **Conceder keyword**: conceder uma keyword a uma carta.
22. **Remover keyword**: remover uma keyword de uma carta.
23. **Negar efeito**: negar o efeito de uma carta.
24. **Proibir ação**: proibir uma ação.
25. **Modificar regra**: modificar um procedimento do motor.
26. **Adicionar à mão**: mover uma carta para a mão.
27. **Adicionar à life**: mover uma carta para a life.
28. **Colocar no fundo**: mover uma carta para o fundo de uma zona.
29. **Colocar no topo**: mover uma carta para o topo de uma zona.
30. **Devolver à mão**: mover uma carta para a mão.
31. **Devolver ao deck**: mover uma carta para o deck.
32. **Descartar da life**: mover uma carta da life para a lixeira.
33. **Adicionar à life do oponente**: mover uma carta para a life do oponente.
34. **Trocar controle**: trocar o controlador de uma carta.
35. **Copiar efeito**: copiar o efeito de uma carta.
36. **Redirecionar alvo**: redirecionar o alvo de um ataque ou efeito.
37. **Cancelar ataque**: cancelar um ataque declarado.
38. **Cancelar batalha**: cancelar uma batalha em andamento.

---

## 12. Efeitos contínuos

Um efeito contínuo pode ser de seis tipos: modificação, proibição, negação, modificação de regra, concessão de keyword ou concessão de habilidade.

### 12.1 Modificação

Uma modificação é composta por alvo, propriedade, operação, valor e zonas afetadas. A operação pode ser adicionar, subtrair, definir, definir base, conceder ou remover. As propriedades estão na seção 18.

### 12.2 Proibição

Uma proibição é composta por ação proibida, filtro de objeto e jogador afetado. As ações proibíveis estão na seção 19.

### 12.3 Negação

Uma negação é composta por padrão de efeito negado, filtro de alvo e condição opcional.

### 12.4 Modificação de regra

Uma modificação de regra é composta por regra alvo, mudança de regra e objetos afetados. A regra alvo referencia um procedimento do motor. A mudança pode ser suprimir, alterar ou substituir.

### 12.5 Concessão de keyword

Uma concessão de keyword é composta por keyword e alvo. As keywords estão na seção 17.

### 12.6 Concessão de habilidade

Uma concessão de habilidade é composta por habilidade concedida e alvo. A habilidade concedida pode ser de qualquer categoria e é recursiva.

---

## 13. Situações substituídas

Uma situação substituída pode ser individual ou coletiva.

Uma situação individual é composta por evento e filtro de sujeito. Descreve um evento que afeta um único objeto ou processamento individual.

Uma situação coletiva é composta por evento, conjunto afetado e predicado. Descreve um evento que afeta um conjunto de objetos simultaneamente.

O escopo da situação determina como o custo é tratado. No escopo individual, o custo é avaliado por objeto ou processamento. No escopo coletivo, o custo é avaliado uma vez pelo evento, independentemente do número de objetos salvos.

---

## 14. Construção de deck

Uma restrição de construção de deck é composta por tipo de restrição, categoria afetada, propriedade afetada, condição e descrição. Os tipos de restrição são:

1. Excluir categoria com propriedade.
2. Limitar cópias.
3. Modificar requisito de cor.
4. Modificar tamanho do deck.
5. Outro.

Uma restrição de construção de deck é uma habilidade permanente com escopo de construção de deck. Não participa da fila de efeitos de partida.

---

## 15. Durações

O catálogo de durações é fechado nesta versão. As durações são:

1. Este turno.
2. Esta batalha.
3. Até o fim do turno.
4. Até o início do próximo turno.
5. Até o fim da batalha.
6. Enquanto a fonte for válida.
7. Permanente.
8. Até o fim da fase atual.
9. Até o próximo refresh do oponente.
10. Até a próxima fase principal.
11. Até o próximo ataque.
12. Até a próxima ativação.
13. Até a próxima compra.

---

## 16. Limites de uso

O catálogo de limites de uso é fechado nesta versão. Os limites são:

1. Uma vez por turno.
2. Uma vez por batalha.
3. Uma vez por partida.
4. Uma vez por fase.
5. Uma vez por ataque.

O limite é por instância de carta. Quando a carta sai da área e retorna ao campo, é tratada como nova instância e pode ativar novamente, salvo quando a regra do limite determinar o contrário.

---

## 17. Keywords

O catálogo de keywords é fechado nesta versão. As keywords são:

1. Rush.
2. Rush: Character.
3. Double Attack.
4. Banish.
5. Blocker.
6. Unblockable.
7. Trigger.
8. Counter.
9. Main.
10. Activate: Main.
11. When Attacking.
12. On Play.
13. On K.O.
14. On Block.
15. On Your Opponent's Attack.
16. End of Your Turn.
17. End of Your Opponent's Turn.
18. DON!! xX.
19. DON!! −X.
20. Your Turn.
21. Opponent's Turn.
22. Once Per Turn.
23. Trash.
24. K.O.

---

## 18. Propriedades

O catálogo de propriedades é fechado nesta versão. As propriedades são:

1. Power.
2. Base Power.
3. Cost.
4. Base Cost.
5. Life.
6. Counter.
7. Nome.
8. Tipo.
9. Atributo.
10. Cor.
11. Keyword.
12. Categoria.
13. Estado (active ou rested).
14. Zona.
15. Controlador.
16. Proprietário.
17. Quantidade de DON dados.
18. Quantidade de cartas na mão.
19. Quantidade de cartas no deck.
20. Quantidade de cartas na life.
21. Quantidade de cartas na lixeira.
22. Quantidade de DON no DON deck.
23. Quantidade de DON na área de custo.

---

## 19. Ações proibíveis

O catálogo de ações proibíveis é fechado nesta versão. As ações são:

1. Jogar Character.
2. Jogar Event.
3. Jogar Stage.
4. Ativar efeito.
5. Atacar.
6. Bloquear.
7. Usar Counter.
8. Dar DON.
9. Restar carta.
10. Tornar carta active.
11. Comprar carta.
12. Descartar carta.
13. Mover carta.
14. K.O. Character.
15. Usar Trigger.
16. Declarar fim da fase principal.
17. Declarar fim do turno.

---

## 20. Estados de cobertura

Todas as estruturas definidas neste documento estão suportadas no nível estrutural. Casos normativos ainda não esclarecidos são registrados como pendências no inventário de regras, mas o modelo possui estrutura para representá-los. Nenhuma estrutura está ausente.

A tabela abaixo enumera as estruturas e seu estado:

1. AutoAbility com todas as variações de ciclo de vida: suportada.
2. ActivateAbility: suportada.
3. PermanentAbility incluindo escopo de construção de deck: suportada.
4. ReplacementAbility individual e coletiva: suportada.
5. Padrões de evento simples, disjunção e conjunção: suportados.
6. Condições estática, histórica, existencial, composta e de dependência: suportadas.
7. Seleção e escolha com papéis de escolhedor e executor: suportadas.
8. Custos com caminhos alternativos e custo adicional: suportados.
9. Operações do catálogo da seção 11: suportadas.
10. Blocos condicionais com os quatro tipos de dependência: suportados.
11. Bloco de repetição: suportado.
12. Efeitos contínuos de modificação, proibição, negação, modificação de regra, concessão de keyword e concessão de habilidade: suportados.
13. Situações substituídas individual e coletiva: suportadas.
14. Restrição de construção de deck: suportada.
15. Limites de uso do catálogo da seção 16: suportados.
16. Papéis de jogador do catálogo da seção 8.3: suportados.
17. Catálogo de operações da seção 11: fechado e suportado.
18. Catálogo de eventos da seção 5.1: fechado e suportado.
19. Catálogo de janelas da seção 5.2: fechado e suportado.
20. Catálogo de keywords da seção 17: fechado e suportado.
21. Catálogo de propriedades da seção 18: fechado e suportado.
22. Catálogo de ações proibíveis da seção 19: fechado e suportado.
23. Catálogo de durações da seção 15: fechado e suportado.
24. Catálogo de predicados de estado da seção 7.2: fechado e suportado.

---

## 21. Critério para iniciar a gramática

A gramática pode começar quando:

1. Todas as estruturas semânticas estão definidas. Cumprido.
2. Não há estruturas ausentes. Cumprido.
3. Os catálogos iniciais existem, estão fechados nesta versão e são extensíveis apenas por revisão formal. Cumprido.
4. As distinções normativas do documento de semântica estão preservadas. Cumprido.
5. A validação com dez cartas exercitou as estruturas principais. Cumprido.
6. As pendências normativas remanescentes estão registradas como questões no inventário, mas o modelo possui estrutura para representá-las. Cumprido.

A gramática deve usar as construções deste documento como base, manter os catálogos abertos apenas para extensão formal e não introduzir semântica nova sem uma regra ou carta que a justifique.