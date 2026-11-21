# XAND Chat — recuperação de conversas

Extensão local do Chrome (Manifest V3), exclusiva do projeto ChatGPT **XAND MUSIC - Capas TikTok**.

## Função
- Observa apenas as páginas do projeto de ID `g-p-6b0182d2a7a0819183158c736eb617ed`.
- Detecta o envio de mensagem na interface, acompanha sinais visíveis de resposta e verifica o estado uma vez por minuto.
- Se surgir erro visível por 45 segundos, ou não houver resposta por 6 minutos, ou o fluxo de texto ficar parado por 9 minutos, tenta **abrir um único chat novo no mesmo projeto**.
- Inicia o chat com um aviso de recuperação, endereço do chat anterior, último pedido capturado antes do envio e, quando disponível, um trecho da resposta anterior. Pede conferência do estado real antes de repetir qualquer ação.
- Nunca apaga chats, não publica nada no TikTok, não acessa Work/Codex, não grava capturas de tela, não chama APIs externas e não lê outros projetos.
- Ao clicar no ícone da extensão dentro do projeto, faz um **teste manual não destrutivo**: abre outro chat identificado como TESTE.

## Instalação no PC de casa
1. Obter esta pasta **diretamente do GitHub** (branch `feature/xand-chat-watchdog`).
2. No Chrome, abrir `chrome://extensions`, ativar o modo de desenvolvedor e clicar em **Carregar sem compactação**.
3. Selecionar a pasta `tools/chatgpt-watchdog`, que contém `manifest.json`.
4. Manter o Chrome aberto e usar o ChatGPT **modo Chat** dentro do projeto XAND.

## Limites reais
- Funciona apenas no **Chrome do computador onde foi instalada** e enquanto o navegador e a extensão estiverem executando. Não observa conversas feitas apenas nos apps do Android ou iOS.
- O Chrome ou o próprio sistema podem suspender verificações em segundo plano.
- A detecção depende da estrutura visual atual do ChatGPT, que pode mudar; portanto é uma tentativa de recuperação, não uma garantia.
- O envio automático do resumo depende do campo de mensagem do site estar acessível. Se falhar, a nova aba do projeto permanece aberta.
- Demoras normais (raciocínio, imagem ou ferramentas) podem parecer travamento. A extensão usa limites conservadores e no máximo uma tentativa por ocorrência.
- O último pedido é capturado do campo de escrita **no momento do envio**, antes de o editor ser limpo; o trecho da resposta anterior é capturado quando disponível. Esses trechos ficam apenas em `chrome.storage.session` durante a sessão do Chrome, sem arquivos ou capturas de tela. Após reiniciar navegador ou extensão, essa memória temporária pode desaparecer.
- Se alguma ação externa do chat anterior ainda estiver em execução, **não** deve ser repetida sem verificar o resultado.

## Teste rápido
1. Abra um chat do projeto no Chrome e clique no ícone da extensão.
2. Confirme que surgiu uma nova aba **dentro do mesmo projeto**, com mensagem de `TESTE`.
3. Confirme que o chat original continua intacto.
4. Não marque músicas como resolvidas durante o teste.

## Status
Versão experimental **0.1.0**. Em 21/11/2026, um teste controlado de falha de rede em uma única aba fez a extensão abrir automaticamente novo chat dentro do projeto, enviar o aviso de recuperação e receber resposta. A simulação de tempo e os testes da captura de contexto são automatizados; **a perda real do serviço e um travamento de seis/nove minutos não foram reproduzidos**. Não há garantia de leitura do histórico completo do chat anterior: somente os trechos capturados, o URL e a memória/instruções do projeto são transferidos.
