import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluatePreflight } from './posting-checks.mjs';

const task = {url:'https://www.tiktok.com/t/xyz',item:{song:'My Life My Rules',artist:'URIYA ZION INGRAM',editor_source_has_branding:true}};
const video = {duration:60000,width:1080,height:1920};
const editor = {authenticated:true,sourceSelected:true,processingJob:null,videoReady:true,download:'/api/download/XAND_MOBILE_test.mp4'};
const sound = {title:'My Life My Rules',artist:'URIYA ZION INGRAM',seconds:60};
const run = (overrides={}) => evaluatePreflight({task,video,editor,tiktokSound:sound,...overrides});

test('compatible durations still require manual review and cannot publish', () => {
  const r=run();assert.equal(r.status,'awaiting_manual_review');assert.equal(r.canPublish,false);assert.equal(r.canProceedToManualReview,true);
});
test('19 second TikTok sound blocks 60 second video',()=>{
  const r=run({tiktokSound:{...sound,seconds:19}});
  assert.equal(r.status,'blocked');assert.ok(r.blocking.includes('tiktok_sound_shorter_than_video'));assert.equal(r.canPublish,false);
});
test('different artist is rejected even with same title',()=>{
  const r=run({tiktokSound:{...sound,artist:'Outro Artista'}});
  assert.ok(r.blocking.includes('tiktok_sound_artist_not_confirmed'));
});
test('missing TikTok sound evidence is rejected',()=>{
  const r=run({tiktokSound:{}});assert.ok(r.blocking.includes('tiktok_sound_duration_missing'));assert.equal(r.canPublish,false);
});
test('unknown export provenance cannot bypass visual review',()=>{
  const r=run();assert.ok(r.manualReviewRequired.some(x=>x.includes('corresponde')));assert.equal(r.canPublish,false);
});
test('export for another selected source is blocked',()=>{
  const r=run({editor:{...editor,sourceSelected:false}});
  assert.ok(r.blocking.includes('editor_source_mismatch'));
});
test('landscape source is blocked',()=>{
  const r=run({video:{duration:60000,width:1920,height:1080}});
  assert.ok(r.blocking.includes('video_not_vertical'));
});
test('unfinished editor render is blocked',()=>{
  const r=run({editor:{...editor,processingJob:'job-123'}});
  assert.ok(r.blocking.includes('render_still_processing'));
});

