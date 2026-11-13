# Changelog — Just in Time (protótipo)

## 0.0.3 — 13/11/2026
- **Repetições**: todo dia, toda semana, a cada 15 dias, todo mês, todo ano e personalizado
  (a cada N dias/semanas/meses/anos). Também **dias e horários específicos**
  (ex.: seg de manhã, qui à noite, sáb à tarde), com atalhos Manhã/Tarde/Noite.
  A próxima ocorrência é criada assim que o prazo chega; datas calculadas a partir do
  primeiro prazo (sem "deriva": todo dia 31 volta a ser 31 depois de fevereiro).
- **Cores** por tarefa (paleta com roxo primeiro): pintam a barra no widget, resumo e bolha.
  Sem cor, a barra continua mostrando a urgência. O cronômetro fica vermelho na última hora.
- **Emojis** como ícone de cada tarefa (opcional), com atalhos dos mais usados.
- **Destaque da próxima tarefa**: no widget e no resumo expandido, a primeira tarefa é maior
  e as seguintes ficam recuadas e menores.
- Widget, resumo e bolha calculam a contagem a partir da mesma base.
- **Bolha redimensionável**: pinça com dois dedos, segurar a bolha para A− / A+, ou o
  controle deslizante nas Preferências.
- Testes automáticos das regras de repetição (10 casos).

## 0.0.2 — 13/11/2026
- Prazo escolhido por **data e hora** (calendário e relógio), em vez de "daqui a X minutos".
- Tela de alarme também abre com o **celular em uso** (permissão "Aparecer sobre outros apps").
- **Notificação-resumo** fixa e silenciosa: recolhida mostra a próxima tarefa com contagem
  regressiva; expandida mostra até 2 tarefas + "+N tarefas".
- **Bolha flutuante** com a próxima tarefa: arrastável, toque abre o app, ✕ esconde até a
  próxima tarefa mudar.
- **Relatório de bugs**: falhas e erros são registrados; o app avisa e oferece "Reportar bug"
  (compartilhar ou copiar o relatório).
- Assinatura fixa no projeto: as próximas versões atualizam sem precisar desinstalar.
- Testes rápidos (alarme em 30 s, simular falha/erro) movidos para a área de Diagnóstico.

## 0.0.1 — 13/11/2026
- Primeiro protótipo de risco: alarme exato (setAlarmClock), tela de alarme sobre o bloqueio,
  notificação com Feito / Não feito / Adiar 5 min, widget com contagem regressiva (até 3 tarefas),
  reagendamento ao ligar o celular, painel de permissões e log de diagnóstico.
- Testado num Samsung Galaxy A31 (Android 12): alarmes com 0 a 1,5 s de atraso.
