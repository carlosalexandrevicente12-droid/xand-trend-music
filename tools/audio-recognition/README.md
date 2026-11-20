# Reconhecimento de audio — XAND MUSIC (etapa experimental)

Este modulo identifica **titulo, artista e genero quando disponivel** a partir de um arquivo de audio ja obtido. Ele gera JSON utilizavel pela futura integracao com a fila do XAND Share no Motorola.

**Estado:** prova de conceito funcional no computador. Ainda **nao esta ligado ao app Android**, nao extrai audio automaticamente do link do TikTok e nao modifica o XAND Music Analytics, o site Radar ou o APK instalado.

## Executar no Windows

Requisitos: [uv](https://github.com/astral-sh/uv), Python 3.12 ou superior e internet.

```powershell
uv run --no-project --python 3.12 --with shazamio==0.9.0 python tools/audio-recognition/reconhecer_audio.py "C:\caminho\audio.mp3"
```

Saida JSON de um teste real em 20/11/2026, com um arquivo de audio ja salvo no projeto:

```json
{"status":"matched","source_audio":"TikTok_7703504073371241237_20261120.mp3","title":"Acurrucar","artist":"Ed Maverick","genre":"Alternative Folk","track_key":"406719839","provider":"ShazamIO"}
```

O campo `genre` pode ser `null`. Se nao houver correspondencia, o sistema retorna `not_found` e nao inventa nomes. Um resultado nao prova que o mesmo audio esta em outros links.

## Testes sem rede

```powershell
uv run --no-project --python 3.12 python -m unittest discover -s tools/audio-recognition/tests -v
```

## Limites e privacidade

- Usa o projeto aberto [ShazamIO](https://github.com/shazamio/ShazamIO), que consulta um servico de terceiros por interface **nao oficial**. A disponibilidade pode mudar sem aviso.
- O processo cria uma assinatura acustica a partir do arquivo local para reconhecimento remoto. Necessita conexao com a internet; nao e reconhecimento 100% offline.
- Nao exige chave paga para o teste, mas uso gratuito e sem limites futuros nao sao garantidos.
- Nao altera, exclui nem marca como resolvido nenhum item da fila.
- Nao publica videos, nao grava audio ambiente nem usa o microfone do aparelho.

## Proxima integracao (ainda pendente)

1. Obter uma amostra autorizada de audio correspondente ao link salvo, ou usar captacao com consentimento do usuario.
2. Executar reconhecimento e associar o resultado ao item correto da fila por identificador.
3. Apresentar titulo, artista e genero editaveis, permitindo confirmacao antes da geracao do video.
4. Manter o botao **Resolvido** sob controle manual do usuario.

O APK `music.xand.share` e o app Audile instalados no Motorola permanecem inalterados por este ramo.
