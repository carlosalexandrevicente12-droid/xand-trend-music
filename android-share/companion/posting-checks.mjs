/**
 * Verificação somente leitura, antes de qualquer publicação da XAND MUSIC.
 * Não autentica no TikTok, não altera rascunhos e nunca publica.
 */
const normalized = value => String(value ?? '').normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('pt-BR');
export function evaluatePreflight({ task, video, editor, tiktokSound }) {
  const blocking = [];
  const manual = [
    'Conferir visualmente a logo oficial completa na parte inferior',
    'Conferir que o MP4 exportado corresponde à música selecionada',
    'Ouvir a reprodução completa no rascunho do TikTok',
    'Revisar descrição, hashtags e privacidade antes de publicar'
  ];
  if (!task?.url) blocking.push('queue_item_missing');
  if (!task?.item?.song || !task?.item?.artist) blocking.push('music_metadata_missing');
  const videoSeconds = Number(video?.duration) / 1000;
  if (!Number.isFinite(videoSeconds) || videoSeconds <= 0) blocking.push('video_duration_missing');
  else if (videoSeconds < 60 || videoSeconds > 180) blocking.push('video_outside_60_180_seconds');
  if (!video?.width || !video?.height || video.width >= video.height) blocking.push('video_not_vertical');
  if (!editor?.authenticated) blocking.push('editor_not_authenticated');
  if (!editor?.sourceSelected) blocking.push('editor_source_mismatch');
  if (editor?.processingJob) blocking.push('render_still_processing');
  if (!editor?.videoReady || !String(editor?.download ?? '').startsWith('/api/download/')) blocking.push('export_not_ready');
  const soundSeconds = Number(tiktokSound?.seconds);
  if (!Number.isFinite(soundSeconds) || soundSeconds <= 0) blocking.push('tiktok_sound_duration_missing');
  else if (Number.isFinite(videoSeconds) && videoSeconds > soundSeconds) blocking.push('tiktok_sound_shorter_than_video');
  if (!tiktokSound?.title || normalized(tiktokSound.title) !== normalized(task?.item?.song)) blocking.push('tiktok_sound_title_not_confirmed');
  if (!tiktokSound?.artist || normalized(tiktokSound.artist) !== normalized(task?.item?.artist)) blocking.push('tiktok_sound_artist_not_confirmed');
  return {
    status: blocking.length ? 'blocked' : 'awaiting_manual_review',
    canProceedToManualReview: blocking.length === 0,
    canPublish: false,
    videoSeconds: Number.isFinite(videoSeconds) && videoSeconds > 0 ? videoSeconds : null,
    tiktokSoundSeconds: Number.isFinite(soundSeconds) && soundSeconds > 0 ? soundSeconds : null,
    blocking, manualReviewRequired: manual
  };
}

