# Roteiro — Just in Time

## Próximas versões
- ~~0.3.0: widget, notificação-resumo e bolha~~ ✅
- ~~0.4.0: contador de entregas~~ ✅ (no topo da lista e no widget, com Hoje/Semana e sequência)
- Assistente de primeira execução (permissões passo a passo, com instruções por fabricante).
- Verificação de atualização pelo site (`versao.json`) e página de instalação.
- Versão de PC (Windows/Linux) com as mesmas regras e telas.

## Ideias guardadas

### Contador de entregas (recompensa e feedback em números) — ✅ feito na 0.4.0
- Quantas tarefas foram **entregues** e **não entregues** no **dia**.
- Dá para trocar para a **semana**, para considerar prazos maiores.
- Objetivo: ser uma espécie de recompensa, mostrando em números quanto foi entregue.
- Base pronta: cada ocorrência já guarda o status (feita/não feita), o prazo e a hora da resposta.
- Decidido: topo da lista + widget; "Feita" conta como entregue mesmo depois de adiar; com sequência de dias.

### Outras
- Integração com o PlaningThings (os IDs em UUID e a exportação em JSON já foram pensados para isso).
- Estatísticas: taxa de cumprimento, tarefas mais adiadas, semanas mais produtivas.
