# PRD — Just in Time

Versão do documento: 18/11/2026. App da NokyLabs, idealizado por Thayna Dias.

## Conceito

O Just in Time é o app de **executar**: mostra o que tem prazo hoje, o tempo passando, toca no prazo e pergunta se a tarefa foi feita. Ele é a versão mobile e mais enxuta do ecossistema de organização da NokyLabs. O **PlaningThings** é o lugar de **planejar**; o Just in Time é o lugar de **fazer acontecer**.

## Problema

Listas de tarefas comuns não mostram o tempo correndo. A pessoa sabe o que precisa fazer, mas perde a noção de quanto falta até o prazo.

## Público

Primeiro a Thay (teste no Galaxy A31). Depois, quem baixar pelo site e pelo launcher do celular da NokyLabs.

## Estado atual (0.5.0-alpha.1)

- Kotlin Multiplatform + Compose, com o NokyLabs.UI. Android 8.0+.
- 41 testes passando; compila sem erros.
- Sem Release, sem tag; chave de assinatura ainda é a de teste.

## O que já existe

- Tarefas com emoji, cor, prazo exato ou dia inteiro e repetições.
- Próxima tarefa com contagem regressiva.
- Alarme exato com tela de alarme; avisos antes do prazo.
- Horário silencioso e resumo do dia.
- Widget e bolha flutuante.
- Contador de entregas.
- Ações "Finalizar" e "Ajustar" no prazo.

## Novo: widget Relógio do Dia

Uma roda de 24 horas na tela inicial do celular.

- **Posições:** meio-dia no topo, meia-noite embaixo, 6h e 18h nas laterais.
- **Arcos coloridos:** cada tarefa aparece como um trecho na cor dela, no horário em que acontece.
- **Ponteiro:** indica a hora atual; o que está à frente dele é o que vem chegando.
- **Toque:** abre o app na tarefa mais próxima.
- **Decidido (18/11):** tarefas de dia inteiro formam um único arco externo, na cor da atividade (ex.: laranja). Mesmo com várias tarefas de dia inteiro, é sempre um arco só, dividido nas cores de cada uma (duas tarefas, duas cores; três, três cores). No widget, o arco fica estático, para poupar bateria; dentro do app, ele gira em volta do relógio com uma animação suave. Tarefas sem duração aparecem como um pontinho no horário em que acontecem.

## Novo: integração com o PlaningThings

- **Divisão:** PlaningThings planeja (blocos, projetos, visão ampla, no PC); Just in Time executa (hoje, prazos, alarmes, no celular).
- **Dados compartilhados:** os dois leem e escrevem na mesma base. Um bloco do PlaningThings com data e hora aparece sozinho no Just in Time.
- **Mão dupla:** marcar "feito" no celular atualiza o bloco no PC; "Ajustar" o prazo no celular muda a data no PlaningThings.
- **Versão de PC:** em vez de um Just in Time para PC, o próprio PlaningThings cumpre esse papel.
- **Decidido (18/11):** os dados ficam no Supabase. Uma tarefa criada no celular vira um bloco e aparece no PlaningThings. O Just in Time não pede dificuldade: a pessoa só informa o horário e o tempo da tarefa, e o bloco entra com a dificuldade padrão, nível 2.

## Requisitos não funcionais

- Permissões de alarme exato, sobreposição e bateria explicadas ao usuário.
- APK de lançamento assinado com a chave da NokyLabs, guardada fora do Git.
- Releases não marcados como pré-lançamento, para o Robô do catálogo encontrar.
- Funciona sem internet; a sincronização acontece quando houver conexão.

## Fora do escopo agora

- Play Store.
- Versão Windows/Linux própria (coberta pelo PlaningThings).

## Roteiro

Ordem decidida em 18/11:

1. **Chave de lançamento e primeiro Release:** workflow de compilação, renomear o branch para `main`, chave de lançamento, `release.yml` e o primeiro Release. A chave vem antes do login, porque o Google pede a impressão digital (SHA-1) dela.
2. **Tela de boas-vindas e login obrigatório.**
3. **Migração das tarefas locais** para a conta, no primeiro login.
4. **Sincronização com o Supabase**, com a cópia local em JSON e a regra "vale a mais recente" tarefa por tarefa.
5. **Widget Relógio do Dia.**
6. **Integração com o PlaningThings:** modelo de dados comum e mão dupla entre os dois apps.

Também previsto, sem posição fixa: Política de Privacidade e botão de apagar a conta; site e launcher (página do app e entrada no catálogo); assistente de primeira execução; aviso de atualização pelo site.

## Riscos

- Trocar a chave de teste pela de lançamento exige desinstalar uma vez (aceito).
- Perder a chave impede atualizar o app.
- A integração depende do PlaningThings ter seu próprio modelo de dados definido.

* **Alarme sem internet:** o alarme nunca pode depender da nuvem; as tarefas ficam guardadas no celular.
* **Conflito de edição:** mudanças ao mesmo tempo no PC e no celular precisam de uma regra clara (ver Sincronização).
* **PlaningThings sem base definida:** o modelo de dados precisa servir aos blocos do PlaningThings, não só ao Just in Time.
* **Bateria da Samsung:** o sistema pode matar o app em segundo plano e afetar alarmes e o widget.
* **Login novo:** entrar com o Google muda a primeira experiência de quem já usa sem conta.

## Sincronização e dados

Decisão de 18/11: os dados ficam no **Supabase**, como no ValReport e nos outros apps da NokyLabs.

- **Arquivo principal na nuvem** e uma **imagem local em JSON** no celular, que garante o funcionamento offline.
- **Vale a modificação mais recente:** se a nuvem mudou por último, o celular atualiza; se o celular mudou por último, ele envia para a nuvem quando voltar a ter internet.
- **Histórico no PC:** o PlaningThings mostra as modificações feitas e tem a opção de desfazer.
- **Comparação por tarefa (decidido em 18/11): cada tarefa guarda a data da última modificação, e a regra "vale a mais recente" é aplicada tarefa por tarefa, não no arquivo inteiro. Assim, mudanças em tarefas diferentes no PC e no celular nunca se apagam.**

## Novo: tela de boas-vindas e login

Na primeira vez que o app abre, aparece uma tela com o nome Just in Time e uma rodinha girando, igual ao arco de dia inteiro, com os botões **Cadastrar** e **Entrar**.

- **Login:** dois caminhos. Em cima, os botões Cadastrar e Entrar, por e-mail e senha (sem nome de usuário). Embaixo, um botão com o ícone do Google para entrar com a conta Google, configurado no Google Cloud Console e no Supabase Auth. O login é obrigatório: não dá para usar o app sem conta. O cadastro por e-mail exige confirmação do e-mail antes de usar o app. Privacidade: a rotina é dado privado; cada pessoa só vê as próprias tarefas (regras de segurança por linha, o RLS do Supabase).
- **Atenção:** o Google pede a impressão digital (SHA-1) da chave que assina o app. Por isso, a chave de lançamento precisa existir antes de configurar o login.

## Bateria

- O app pede para ficar fora da otimização de bateria, com uma explicação do motivo.
- Nos celulares Samsung, também vale orientar a pessoa a pôr o app na lista "Apps que nunca entram em suspensão".

## Pontos adicionados (18/11)

- **Migração das tarefas locais:** no primeiro login, as tarefas que já estão no celular sobem para a conta, sem perder nada (inclui as tarefas atuais do A31).
- **Privacidade e LGPD:** Política de Privacidade do app e um botão para apagar a conta e todos os dados.
- **Ordem no roteiro:** chave de lançamento → tela de boas-vindas e login obrigatório → migração das tarefas → sincronização com o Supabase → widget Relógio do Dia → integração com o PlaningThings.

## Ideias para depois

- **Esqueci a senha:** recuperação de senha por e-mail.
- **Aviso de novo acesso:** e-mail avisando quando a conta entrar de outro aparelho.
