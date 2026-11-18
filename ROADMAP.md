# Roteiro — Just in Time

O documento de produto completo está em [docs/PRD.md](docs/PRD.md). Este roteiro segue a ordem decidida em 18/11/2026.

## Já entregue
- ~~0.3.0: widget, notificação-resumo e bolha~~ ✅
- ~~0.4.0: contador de entregas~~ ✅ (no topo da lista e no widget, com Hoje/Semana e sequência)
- ~~0.5.0: Finalizar e Ajustar, editor em blocos e avisos personalizados~~ ✅

## Próximos passos (ordem decidida em 18/11)
1. **Chave de lançamento e primeiro Release:** workflow de compilação, renomear o branch para `main`, chave da NokyLabs guardada fora do Git, `release.yml` e o primeiro Release (sem marcar como pré-lançamento, para o Robô do catálogo encontrar). Vem primeiro porque o login com o Google pede a impressão digital (SHA-1) dessa chave. Trocar a chave de teste exige desinstalar o app uma vez (aceito).
2. **Tela de boas-vindas e login obrigatório:** nome Just in Time com a rodinha girando e os botões **Cadastrar** e **Entrar**. E-mail e senha (com confirmação do e-mail antes de usar) ou conta Google, pelo Supabase Auth. Cada pessoa só vê as próprias tarefas (RLS).
3. **Migração das tarefas locais:** no primeiro login, as tarefas que já estão no celular sobem para a conta, sem perder nada (inclui as do A31).
4. **Sincronização com o Supabase:** arquivo principal na nuvem e cópia local em JSON no celular, para funcionar sem internet. Vale a modificação mais recente, **tarefa por tarefa**. O alarme nunca depende da nuvem.
5. **Widget Relógio do Dia:** roda de 24 horas (meio-dia no topo, meia-noite embaixo), arcos na cor de cada tarefa, ponteiro na hora atual e toque que abre a tarefa mais próxima. Tarefas de dia inteiro formam um único arco externo dividido nas cores de cada uma (estático no widget, girando dentro do app); tarefas sem duração viram um pontinho.
6. **Integração com o PlaningThings:** mesma base no Supabase, em mão dupla. Um bloco com data e hora aparece no Just in Time; uma tarefa criada no celular vira um bloco com a dificuldade padrão (nível 2); "feito" e "Ajustar" atualizam o bloco no PC.

## Também previsto
- **Privacidade e LGPD:** Política de Privacidade do app e botão para apagar a conta e todos os dados.
- **Site e launcher:** página do app e entrada no catálogo da NokyLabs.
- **Assistente de primeira execução:** permissões passo a passo (alarme exato, sobreposição e bateria), com instruções por fabricante, incluindo a lista "Apps que nunca entram em suspensão" dos Samsung.
- **Aviso de atualização pelo site** (`versao.json`).

## Fora do escopo agora
- Play Store.
- Versão Windows/Linux própria: o PlaningThings cumpre esse papel no PC.

## Ideias guardadas

### Contador de entregas (recompensa e feedback em números) — ✅ feito na 0.4.0
- Quantas tarefas foram **entregues** e **não entregues** no **dia**.
- Dá para trocar para a **semana**, para considerar prazos maiores.
- Objetivo: ser uma espécie de recompensa, mostrando em números quanto foi entregue.
- Base pronta: cada ocorrência já guarda o status (feita/não feita), o prazo e a hora da resposta.
- Decidido: topo da lista + widget; "Feita" conta como entregue mesmo depois de adiar; com sequência de dias.

### Outras
- ~~Integração com o PlaningThings~~ → agora é o passo 6 do roteiro (os IDs em UUID e a exportação em JSON já foram pensados para isso).
- Estatísticas: taxa de cumprimento, tarefas mais adiadas, semanas mais produtivas.
- **Esqueci a senha:** recuperação de senha por e-mail.
- **Aviso de novo acesso:** e-mail avisando quando a conta entrar de outro aparelho.
