db.f_post.find({ 'reactions.horsey': { $exists: true } }).forEach(p => {
  db.f_post.update(
    { _id: p._id },
    {
      $set: {
        'reactions.PlayStrategy': p.reactions.horsey,
      },
      $unset: {
        'reactions.horsey': '',
      },
    },
  );
});
