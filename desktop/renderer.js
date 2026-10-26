const $ = id => document.getElementById(id);
let loaded = false;
function render({ status, connection }) {
  $('status').textContent = status.message;
  $('dot').className = ['error', 'unavailable'].includes(status.state) ? 'error' : '';
  const busy = ['connecting', 'approving', 'streaming'].includes(status.state);
  $('disconnect').disabled = !busy;
  $('connect').disabled = busy || ['starting', 'unavailable'].includes(status.state);
  $('address').disabled = busy;
  if (!loaded && connection?.address) $('address').value = connection.address;
  loaded = true;
}
window.micbridge.onUpdate(render);
window.micbridge.state().then(render);
$('pair-form').onsubmit = async event => { event.preventDefault(); await window.micbridge.connect($('address').value); };
$('disconnect').onclick = () => window.micbridge.disconnect();
