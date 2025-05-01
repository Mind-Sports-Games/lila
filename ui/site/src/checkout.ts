import * as xhr from 'common/xhr';

const $checkout = $('div.plan_checkout');
const getFreq = () => $checkout.find('group.freq input:checked').val();
const showErrorThenReload = (error: string) => {
  alert(error);
  location.assign('/patron');
};

export function checkoutStart(stripePublicKey: string) {
  const lifetime = {
    cents: parseInt($checkout.data('lifetime-cents')),
    usd: $checkout.data('lifetime-usd'),
  };

  const min = 100,
    max = 100 * 100000;

  if (location.hash === '#onetime') $('#freq_onetime').trigger('click');
  if (location.hash === '#lifetime') $('#freq_lifetime').trigger('click');

  // Other is selected but no amount specified
  // happens with backward button
  if (!$checkout.find('.amount_choice group.amount input:checked').data('amount'))
    $checkout.find('#plan_monthly_1000').trigger('click');

  const onFreqChange = function () {
    const freq = getFreq();
    $checkout.find('.amount_fixed').toggleClass('none', freq != 'lifetime');
    $checkout.find('.amount_choice').toggleClass('none', freq == 'lifetime');
    const sub = freq == 'monthly';
    $checkout.find('.paypal--order').toggle(!sub);
    $checkout.find('.paypal--subscription').toggle(sub);
  };
  onFreqChange();

  $checkout.find('group.freq input').on('change', onFreqChange);

  $checkout.find('group.amount .other label').on('click', function (this: HTMLLabelElement) {
    let amount: number;
    const raw: string = prompt(this.title) || '';
    try {
      amount = parseFloat(raw.replace(',', '.').replace(/[^0-9\.]/gim, ''));
    } catch (e) {
      return false;
    }
    let cents = Math.round(amount * 100);
    if (!cents) {
      $(this).text($(this).data('trans-other'));
      $checkout.find('#plan_monthly_1000').trigger('click');
      return false;
    }
    if (cents < min) cents = min;
    else if (cents > max) cents = max;
    const usd = '$' + cents / 100;
    $(this).text(usd);
    $(this).siblings('input').data('amount', cents).data('usd', usd);
  });

  const getAmountToCharge = () => {
    const freq = getFreq(),
      cents =
        freq == 'lifetime' ? lifetime.cents : parseInt($checkout.find('group.amount input:checked').data('amount'));
    if (!cents || cents < min || cents > max) return;
    const amount = cents / 100;
    return amount;
  };

  $checkout.find('button.paypal').on('click', function () {
    const amount = getAmountToCharge();
    const $form = $checkout.find('form.paypal_checkout.' + getFreq());
    $form.find('input.amount').val('' + amount);
    ($form[0] as HTMLFormElement).submit();
    $checkout.find('.service').html(playstrategy.spinnerHtml);
    // $checkout.find('.service .paypal--disabled').toggleClass('none', true);
    // $checkout.find('.service .paypal:not(.paypal--disabled)').toggleClass('none', false);
  });

  const queryParams = new URLSearchParams(location.search);
  for (const name of ['dest', 'freq']) {
    if (queryParams.has(name))
      $(`input[name=${name}][value=${queryParams.get(name)?.replace(/[^a-z_-]/gi, '')}]`).trigger('click');
  }

  payPalOrderStart($checkout, getAmountToCharge);
  payPalSubscriptionStart($checkout, getAmountToCharge);
  stripeStart($checkout, stripePublicKey, getAmountToCharge);
}

const xhrFormData = ($checkout: Cash, amount: number) =>
  xhr.form({
    email: $checkout.data('email'),
    amount,
    freq: getFreq(),
  });

const payPalStyle = {
  layout: 'horizontal',
  color: 'blue',
  height: 55,
};

function payPalOrderStart($checkout: Cash, getAmount: () => number | undefined) {
  (window.paypalOrder as any)
    .Buttons({
      style: payPalStyle,
      createOrder: (_data: any, _actions: any) => {
        const amount = getAmount();
        if (!amount) return;
        return xhr
          .jsonAnyResponse(`/patron/paypal/checkout?currency=USD`, {
            method: 'post',
            body: xhrFormData($checkout, amount),
          })
          .then(res => res.json())
          .then(data => {
            if (data.error) showErrorThenReload(data.error);
            else if (data.order?.id) return data.order.id;
            else location.assign('/patron');
          });
      },
      onApprove: (data: any, _actions: any) => {
        xhr
          .json('/patron/paypal/capture/' + data.orderID, { method: 'POST' })
          .then(() => location.assign('/patron/thanks'));
      },
    })
    .render('.paypal--order');
}

function payPalSubscriptionStart($checkout: Cash, getAmount: () => number | undefined) {
  (window.paypalSubscription as any)
    .Buttons({
      style: payPalStyle,
      createSubscription: (_data: any, _actions: any) => {
        const amount = getAmount();
        if (!amount) return;
        return xhr
          .jsonAnyResponse(`/patron/paypal/checkout?currency=USD`, {
            method: 'post',
            body: xhrFormData($checkout, amount),
          })
          .then(res => res.json())
          .then(data => {
            if (data.error) showErrorThenReload(data.error);
            else if (data.subscription?.id) return data.subscription.id;
            else location.assign('/patron');
          });
      },
      onApprove: (data: any, _actions: any) => {
        xhr
          .json(`/patron/paypal/capture/${data.orderID}?sub=${data.subscriptionID}`, { method: 'POST' })
          .then(() => location.assign('/patron/thanks'));
      },
    })
    .render('.paypal--subscription');
}

function stripeStart($checkout: Cash, publicKey: string, getAmount: () => number | undefined) {
  const stripe = window.Stripe(publicKey);
  $checkout.find('button.stripe').on('click', function () {
    const amount = getAmount();
    if (!amount) return;
    $checkout.find('.service').html(playstrategy.spinnerHtml);

    xhr
      .jsonAnyResponse(`/patron/stripe/checkout`, {
        method: 'post',
        body: xhrFormData($checkout, amount),
      })
      .then(res => res.json())
      .then(data => {
        if (data.error) showErrorThenReload(data.error);
        else if (data.session?.id) {
          stripe
            .redirectToCheckout({
              sessionId: data.session.id,
            })
            .then((result: any) => showErrorThenReload(result.error.message));
        } else {
          location.assign('/patron');
        }
      }, showErrorThenReload);
  });

  // Close Checkout on page navigation:
  $(window).on('popstate', function () {
    window.stripeHandler.close();
  });
}
