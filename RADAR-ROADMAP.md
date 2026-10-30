# XAND Music Radar — MVP

Laboratório gratuito e local para o perfil **@xanndmusic**. O projeto está integrado ao site existente `index.html` sem substituí-lo.

## Como abrir

- Site existente: `index.html`
- Laboratório novo: `radar-lab.html`
- Estilo: `radar-lab.css`
- Lógica: `radar-lab.js`

O workflow `.github/workflows/publicar-site.yml` publica os quatro arquivos no GitHub Pages após commits em `main` (desde que Pages/Actions estejam habilitados no repositório).

## Funções implementadas

1. Pesquisas por estilo, tipo de vocal e região; os links abrem buscas **reais do TikTok**, não simulam resultados ou um ranking.
2. Catálogo local de músicas com título, artista, link do TikTok, gênero e vocal **informados pelo usuário**.
3. Controle de inéditas, em avaliação, já publicadas e descartadas.
4. Análise de áudio da aba compartilhada via **Web Audio API + getDisplayMedia**, sem baixar o vídeo: nível de sinal, porcentagem relativa de energia nos graves e **estimativa** de BPM.
5. Associação manual das medições à música selecionada.
6. Comparação simples de registros com menos de 1.000 visualizações e 1.000 ou mais.
7. Importação/exportação de CSV e JSON; dados guardados em `localStorage` no próprio navegador.

## Análise sem download: procedimento

1. No Chrome ou Edge para desktop, abra a música em uma aba do TikTok.
2. No Radar, cadastre e selecione a música.
3. Clique em **Compartilhar aba com áudio**.
4. Na janela do navegador, escolha a **aba do TikTok** e confirme **Compartilhar áudio da guia**.
5. Reproduza o áudio, aguarde pelo menos 8 segundos e clique em **Salvar medições na música selecionada**.
6. Clique em **Parar análise**.

A ferramenta **não lê a trilha de áudio dos iframes oficiais do TikTok**. O compartilhamento é opcional e explícito; pode não funcionar em navegadores móveis ou quando o navegador não disponibilizar áudio de aba. O vídeo não é gravado, não é enviado a servidor e não há download de arquivos na análise.

### Limitações honestas

- O BPM estimado pode falhar em áudio com ritmo irregular, contratempos ou pouco grave.
- A relação de energia de 40–180 Hz é um indicador relativo, não medição profissional/calibrada.
- Não existe classificação automática de voz, gênero ou emoção na versão atual; os campos são manuais.
- Não há extração automatizada de tendências, métricas de retenção ou histórico do TikTok; o registro é feito pelo usuário e/ou por importação.
- Não há API paga, backend ou GPU.
- Os dados locais **não sincronizam entre aparelhos**. Use Exportar JSON para fazer uma cópia ou transferir registros.
- Sem dados suficientes, a ferramenta não deduz causa e efeito entre música, vídeo e alcance.

## Prioridades seguintes

1. Criar vínculo de vários posts ao mesmo áudio, incluindo datas, visuais, retenção e engajamento.
2. Adicionar gráficos comparativos com normalização por idade da publicação.
3. Ampliar classificação audiovisual por WebAssembly para arquivos autorizados/localmente disponíveis.
4. Pesquisar APIs **oficiais e autorizadas** para metadados de músicas e estatísticas da conta, sem scraping ou dependência de API paga.
5. Portar a interface e a lógica para **React + TypeScript no Lovable**, mantendo as regras de dados reais e os avisos de limitações.
6. Sincronização opcional futura, mediante configuração explícita e política de acesso; não publicar credenciais em frontend.

## Passagem para Lovable

Fornecer `radar-lab.html`, `radar-lab.css` e `radar-lab.js` como referência funcional. Migrar telas para componentes React e estado local, preservando `getDisplayMedia`, `AudioContext`, interrupção de tracks, exportação de JSON/CSV, validação de URLs, modo sem backend e ausência de métricas falsas.

*Não expor arquivos de áudio, identificadores privados ou segredos em um repositório público.*
