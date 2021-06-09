function playstrategyOrchestra() {
  const load = (instrument, index, filename) =>
    playstrategy.sound.loadOggOrMp3(
      `orchestra.${instrument}.${index}`,
      `${playstrategy.sound.baseUrl}/instrument/${instrument}/${filename}`
    );

  const volumes = {
      celesta: 0.3,
      clav: 0.2,
      swells: 0.8,
    },
    noteOverlap = 15,
    noteTimeout = 300,
    maxPitch = 23;

  let currentNotes = 0;

  // load celesta and clav sounds
  for (let i = 1; i <= 24; i++) {
    if (i > 9) fn = 'c0' + i;
    else fn = 'c00' + i;
    load('celesta', i - 1, fn);
    load('clav', i - 1, fn);
  }
  // load swell sounds
  for (let i = 1; i <= 3; i++) load('swells', i - 1, `swell${i}`);

  const play = (instrument, pitch) => {
    pitch = Math.round(Math.max(0, Math.min(maxPitch, pitch)));
    if (instrument === 'swells') pitch = Math.floor(pitch / 8);
    if (currentNotes < noteOverlap) {
      currentNotes++;
      playstrategy.sound.play(`orchestra.${instrument}.${pitch}`, volumes[instrument]);
      setTimeout(() => {
        currentNotes--;
      }, noteTimeout);
    }
  };

  play('swells', 0);

  return {
    play: play,
  };
}
