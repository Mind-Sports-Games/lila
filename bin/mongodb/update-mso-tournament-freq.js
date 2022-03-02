db.tournament2.update({ name: /.*MSO 2021.*/ }, { $set: { schedule: { freq: 'mso21' } } });
