# Changelog — Just in Time

## 0.2.0-alpha.1 — 13/11/2026
Alarme completo, trazido do protótipo para a base nova.
- **Tela de alarme** com o NokyLabs.UI: acende o celular bloqueado e, com "aparecer sobre outros apps",
  abre também com o celular em uso. Feito / Não feito (com motivo) / Adiar (15 min, 1 h, amanhã).
- **Som de alarme insistente** que para sozinho depois de 60 s (a notificação continua, sem som).
- **Avisos antes do prazo** funcionando (1 dia, 1 h, 15 min).
- **Horário silencioso** (padrão 21:00–08:00): alarmes e avisos chegam sem som, menos as tarefas com
  "tocar mesmo no silêncio". Quando o silêncio acaba, uma notificação lembra o que ficou pendente.
- **Resumo do dia** (padrão 20:30) com as tarefas de dia inteiro ainda abertas. Tarefas de dia inteiro
  vencem às 23:59 em silêncio.
- **Configurações**: permissões (com o porquê de cada uma e o botão Liberar), horário silencioso, resumo
  do dia, testar alarme em 30 s, reportar bug e créditos. O ícone fica amarelo-âmbar quando falta permissão.
- **Relatório de bugs**: falhas são registradas; o app avisa ("Ops, algo deu errado") e oferece Reportar
  ou Copiar. Erros em segundo plano chegam como notificação.
- Banco de dados versão 2 (preferências), com migração: quem tem a 0.1.0 não perde nenhuma tarefa.
- 29 testes automáticos (novos: lembretes, horário silencioso e migração).

## 0.1.0-alpha.1 — 13/11/2026
Primeira versão do app de verdade (a base), feita a partir do que o protótipo validou.
- Projeto Kotlin Multiplatform: `shared` (regras, banco e telas) + `androidApp`.
- Visual com o **NokyLabs.UI para Compose** (submodule), na cor do Just in Time `#FFD60A`.
- Modelo **Tarefa + Ocorrência** em SQLite (SQLDelight), com IDs em UUID.
- Repetições em hora local: todo dia, toda semana, a cada 15 dias, todo mês, todo ano, a cada N
  dias/semanas/meses/anos, e dias + horários específicos (atalhos Manhã/Tarde/Noite).
- Prazo com horário exato ou "dia inteiro" (vence às 23:59).
- Lista com a próxima tarefa em destaque e contagem regressiva; as seguintes recuadas e menores.
- Responder na lista: Feito, Não feito (motivo opcional: Esqueci, Faltou tempo, Desisti, Outro), Adiar (+15 min, +1 h, amanhã).
- Histórico de tarefas respondidas.
- Criar/editar com emoji, cor, data e hora, repetição, avisos antes do prazo (guardados; os avisos tocam
  na próxima etapa) e "tocar mesmo no horário silencioso".
- Aviso de tarefa com o mesmo nome ou no mesmo horário (criar mesmo assim, ajustar ou editar a existente).
- Excluir tarefa (com confirmação).
- Alarme exato (`setAlarmClock`) com notificação Feito / Não feito / Adiar 15 min; alarmes refeitos ao
  ligar o celular, mudar a hora/fuso ou atualizar o app.
- 20 testes automáticos (repetições e regras).

## Protótipo (0.0.1 a 0.0.4)
O histórico do protótipo está na pasta `prototipo/` e nas tags `prototipo-v0.0.1` a `prototipo-v0.0.4`.
