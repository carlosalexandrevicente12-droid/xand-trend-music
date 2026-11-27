# Ponte XAND MUSIC → Editor no Motorola G82

Este componente pertence **somente à automação do TikTok da XAND MUSIC**. Ele controla a interface **já instalada** do XAND MUSIC Editor no Chrome do Motorola por ADB/DevTools, sem modificar ou reinstalar o editor. Não publica nas redes sociais.

## Requisitos

- Node.js 24 ou superior no computador de casa.
- ADB autorizado via Wi-Fi no Moto G82, com o pacote `music.xand.share` instalado.
- Motorola e editor na rede local; editor existente em `http://192.168.0.6:1890/` com sessão já autenticada no Chrome.
- Executar este script no computador de casa (não dentro do Chrome/Android). Não expor a porta local do DevTools à internet.

## Execução

No PowerShell, ajustar os caminhos e a porta ADB atualmente ativa:

```powershell
$env:XAND_ADB_PATH = 'C:\Users\Windows 11\Documents\Codex\Tools\platform-tools\adb.exe'
$env:XAND_ADB_SERIAL = '192.168.0.24:42357'
node .\android-share\companion\editor-bridge.mjs inspect
node .\android-share\companion\editor-bridge.mjs prepare
node .\android-share\companion\editor-bridge.mjs run
```

Comandos independentes:

| Comando | Ação | Segurança |
| --- | --- | --- |
| `inspect` | Lê primeira música da fila, vídeo local e estado do editor | Nenhuma exportação |
| `upload` | Envia vídeo da fila pelo editor autenticado | Transfere em memória, sem criar arquivos temporários no PC |
| `prepare` | Define vídeo, formato 9:16, duração de 1–3 min e títulos vazios para manter arte integrada | Não renderiza |
| `render` | Solicita exportação pelo editor | Recusa reexportar se existe trabalho ou resultado sem `--force-render` |
| `download` | Baixa resultado para Downloads do Motorola e verifica presença | Não exclui originais |
| `run` | Tenta enviar, preparar, exportar, aguardar até 3 minutos e baixar, em sequência | Reaproveita resultado existente; nunca publica |

O comando `run` reúne as etapas em uma chamada. Alternativamente, se `prepare` indicar `source_not_uploaded`, usar `upload` e depois `prepare`. A exportação é **assíncrona**: consultar `inspect` até o editor confirmar vídeo pronto e então chamar `download`. Quando o tempo de espera do `run` termina, a exportação continua no editor; não reenvie nem reexporte cegamente.

Para um **teste com uma nova versão** da mesma música, sem alterar os metadados existentes da fila, `--source` aceita um MP4 já indexado pelo MediaStore em `/sdcard/Movies/XAND MUSIC/`. O script consulta dimensões, tamanho e duração reais. Se esse arquivo for enviado pela primeira vez, `run` exportará a nova versão; se já existir um resultado, exigirá `--force-render` para não repetir automaticamente.

O script `brand-compose.ps1` usa a imagem-base gerada pela Central de Design e lê a **logo oficial original** da instalação do app Android. Faz composição proporcional e exporta uma capa PNG e um MP4 vertical 9:16 de 60 a 180 segundos. As saídas recebem nomes novos; a logo e todos os arquivos anteriores permanecem preservados.

**Conferência visual obrigatória:** o script não consegue comprovar por leitura de campos se a logo está na posição inferior; sinaliza `manual_required`. Não publicar até confirmar logo completa, sem duplicação, posicionada abaixo conforme a identidade XAND MUSIC. Quando a arte já inclui a marca, o script esvazia título e subtítulo para evitar camadas repetidas; não adiciona nova logo. A configuração de áudio original 0% / música 100% é exclusiva do TikTok e **não é aplicada pelo editor**.

O envio lê o arquivo do MediaStore via ADB e o transmite ao endpoint **do próprio editor** pela sessão do Chrome, sem extrair cookies ou senhas. Nenhum screenshot é salvo. Não há agendamento ou publicação automática ativados por este componente; a ativação futura depende de testes de recuperação de falhas, validação visual e autorização de publicação.
