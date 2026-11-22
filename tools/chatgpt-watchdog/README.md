# XAND Chat — recuperação de conversas

Extensão local do Chrome (Manifest V3), exclusiva do projeto ChatGPT **XAND MUSIC - Capas TikTok**.

## Função
- Observa apenas as páginas do projeto de ID `g-p-6b0182d2a7a0819183158c736eb617ed`.
- Detecta o envio de mensagem na interface, acompanha sinais visíveis de resposta e verifica o estado uma vez por minuto.
- Se surgir erro visível por 45 segundos, ou não houver resposta por 6 minutos, ou o fluxo de texto ficar parado por 9 minutos, tenta **abrir um único chat novo no mesmo projeto**.
- Se o chat ativo exibir **aviso explícito de limite da conversa** (português ou inglês), tenta iniciar um novo chat do mesmo projeto, mesmo sem mensagem pendente. Não existe leitura confiável de quantas mensagens ainda cabem.
- Inicia o chat com aviso de continuidade, endereço do chat anterior, último pedido capturado, trecho da resposta anterior e até seis extratos dos turnos recentes visíveis no navegador. Pede conferência do estado real antes de repetir qualquer ação.
- Cada transferência tem identificação própria; se a mensagem já aparecer no novo chat, a extensão reconhece e não a envia uma segunda vez.
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
- O último pedido é capturado do campo de escrita **no momento do envio**, antes de o editor ser limpo; a resposta anterior e os últimos turnos da tela são capturados quando disponíveis. Esses trechos ficam apenas em `chrome.storage.session` durante a sessão do Chrome, sem arquivos ou capturas de tela. Após reiniciar navegador ou extensão, essa memória temporária pode desaparecer.
- **Não é migração completa do histórico**: mensagens antigas, anexos, telas não carregadas e ações de ferramentas podem faltar. O contexto inclui referência à conversa anterior, mas o novo chat pode não conseguir abrir todo o conteúdo desse endereço. Consultar o estado dos sistemas originais continua necessário.
- A detecção de limite só observa **abas ativas** do projeto. A extensão não abre chats para abas antigas deixadas em segundo plano; os avisos precisam corresponder ao texto visível reconhecido.
- O texto transferido é limitado a 6 mil caracteres para não bloquear o campo de escrita; todos os extratos são parciais.
- Se alguma ação externa do chat anterior ainda estiver em execução, **não** deve ser repetida sem verificar o resultado.

## Teste rápido
1. Abra um chat do projeto no Chrome e clique no ícone da extensão.
2. Confirme que surgiu uma nova aba **dentro do mesmo projeto**, com mensagem de `TESTE`.
3. Confirme que o chat original continua intacto.
4. Não marque músicas como resolvidas durante o teste.

## Status
Versão experimental **0.2.0**. A versão anterior 0.1.0 teve um teste controlado de falha de rede em 21/11/2026. A detecção de limite, a seleção dos trechos e a prevenção de duplicação da versão 0.2.0 possuem testes automatizados; o aviso de limite real da interface não foi reproduzido no navegador. O novo chat não contém o histórico integral do antigo.

