# Changelog — Just in Time

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
