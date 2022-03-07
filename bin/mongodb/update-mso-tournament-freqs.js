db.tournament2.updateMany({ name: /.*MSO 2021.*/ }, { $set: { 'schedule.freq': 'mso21' } });
