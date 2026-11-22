# XAND MUSIC — Cliente de controle Motorola (branch isolada)

Implementação opcional, não instalada no Moto G82. Mantém o aplicativo atual, a fila e o rascunho TikTok inalterados.

## Como funciona nesta versão

- No app Android, a tela **Conectar ao controle do iPhone** permite digitar o código de uso único criado na interface `/iphone-control/` do Analytics.
- O aparelho envia o código à API HTTPS no domínio Cloudflare Worker e recebe uma credencial de aparelho. Essa credencial é criptografada com chave no Android Keystore; não vai para o GitHub ou para o painel do iPhone.
- Enquanto essa tela está aberta, o Android informa seu estado à nuvem a cada 20 segundos e recebe **somente** o comando `refresh_status`. A leitura da quantidade de músicas é feita sem modificar a fila local.
- Nenhuma atividade de postar, criar vídeo, escolher música ou editar rascunhos foi acrescentada.

## Limites importantes

1. A API e o painel ainda precisam ser implantados pela branch `feature/iphone-control-bridge` do repositório `studio-stream`, com a migração da tabela D1 verificada e backup prévio.
2. Esta primeira versão não consulta em segundo plano. Com a tela fechada, o iPhone marcará o aparelho offline após cerca de dois minutos. Para controle permanente, estudar mecanismo compatível com limites de bateria Android (push/foreground service), sem habilitar permissões sensíveis automaticamente.
3. O token de um aparelho deve ser revogado na central do iPhone. Não use o código com serviços ou pessoas desconhecidos.
4. A versão instalada no aparelho **não recebe essas mudanças até compilação, validação e instalação autorizada**. Nenhuma instalação foi feita.

## Validação

- Compilar com o fluxo existente de GitHub Actions `Android share APK` na branch `feature/motorola-control-client`.
- Testar sem tocar na fila ou nos rascunhos, começando com ambiente de backend implantado e sem contas TikTok vinculadas.
- Confirmar que os comandos `publish`, `create_video` e equivalentes retornam rejeição no servidor.

Essa conexão é uma infraestrutura de consulta. Não substitui a aprovação final manual de cada post.
