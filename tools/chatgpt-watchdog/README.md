# XAND Chat — recuperação de conversas

Extensão local do Chrome (Manifest V3), exclusiva do projeto ChatGPT **XAND MUSIC - Capas TikTok**.

## Função
- Observa apenas as páginas do projeto de ID `g-p-6b0182d2a7a0819183158c736eb617ed`.
- Detecta o envio de mensagem na interface, acompanha sinais visíveis de resposta e verifica o estado uma vez por minuto.
- Se surgir erro visível por 45 segundos, ou não houver resposta por 6 minutos, ou o fluxo de texto ficar parado por 9 minutos, tenta **abrir um único chat novo no mesmo projeto**.
- Inicia o chat com um aviso de recuperação, endereço do chat anterior e trecho da última solicitação, pedindo conferência do trabalho antes de repetir qualquer ação.
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
- O trecho da última mensagem é lido apenas no momento da falha. O estado temporário da extensão fica em `chrome.storage.session`, sem armazenar conteúdo de conversas em disco.
- Se alguma ação externa do chat anterior ainda estiver em execução, **não** deve ser repetida sem verificar o resultado.

## Teste rápido
1. Abra um chat do projeto no Chrome e clique no ícone da extensão.
2. Confirme que surgiu uma nova aba **dentro do mesmo projeto**, com mensagem de `TESTE`.
3. Confirme que o chat original continua intacto.
4. Não marque músicas como resolvidas durante o teste.

## Status
Versão experimental **0.1.0**. Necessário validar visualmente no Chrome em uso; não considerar proteção em segundo plano confiável antes do teste.
