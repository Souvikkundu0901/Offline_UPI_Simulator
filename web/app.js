const storeKey = "offline-upi-web-store-v1";

const state = {
  currentUserUpi: null,
  lastPacket: null,
  toastTimer: null
};

const elements = {
  authView: document.querySelector("#authView"),
  dashboardView: document.querySelector("#dashboardView"),
  loginForm: document.querySelector("#loginForm"),
  registerForm: document.querySelector("#registerForm"),
  loginUpi: document.querySelector("#loginUpi"),
  loginPin: document.querySelector("#loginPin"),
  registerName: document.querySelector("#registerName"),
  registerMobile: document.querySelector("#registerMobile"),
  registerUpi: document.querySelector("#registerUpi"),
  registerPin: document.querySelector("#registerPin"),
  currentName: document.querySelector("#currentName"),
  currentUpi: document.querySelector("#currentUpi"),
  balanceText: document.querySelector("#balanceText"),
  receiverUpi: document.querySelector("#receiverUpi"),
  paymentAmount: document.querySelector("#paymentAmount"),
  packetId: document.querySelector("#packetId"),
  paymentPin: document.querySelector("#paymentPin"),
  walletAmount: document.querySelector("#walletAmount"),
  walletForm: document.querySelector("#walletForm"),
  paymentForm: document.querySelector("#paymentForm"),
  retryButton: document.querySelector("#retryButton"),
  generatePacketButton: document.querySelector("#generatePacketButton"),
  lastPacketText: document.querySelector("#lastPacketText"),
  historyBody: document.querySelector("#historyBody"),
  usersBody: document.querySelector("#usersBody"),
  logoutButton: document.querySelector("#logoutButton"),
  toast: document.querySelector("#toast"),
  heroPacket: document.querySelector("#heroPacket")
};

function loadStore() {
  const raw = localStorage.getItem(storeKey);
  if (!raw) {
    return { users: [], receipts: {} };
  }
  try {
    const data = JSON.parse(raw);
    return {
      users: Array.isArray(data.users) ? data.users : [],
      receipts: data.receipts && typeof data.receipts === "object" ? data.receipts : {}
    };
  } catch (error) {
    return { users: [], receipts: {} };
  }
}

function saveStore(store) {
  localStorage.setItem(storeKey, JSON.stringify(store));
}

function money(value) {
  return Number(value || 0).toFixed(2);
}

function parseAmount(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) {
    return null;
  }
  return Math.round(amount * 100) / 100;
}

function normalizeUpi(value) {
  return value.trim().toLowerCase();
}

function makeId(prefix, length) {
  const source = crypto.getRandomValues(new Uint8Array(16));
  const token = Array.from(source, item => item.toString(16).padStart(2, "0")).join("");
  return `${prefix}${token.slice(0, length).toUpperCase()}`;
}

function findUser(store, upiId) {
  return store.users.find(user => user.upiId.toLowerCase() === upiId.toLowerCase());
}

function packetFingerprint(packet) {
  return `${packet.senderUpiId.toLowerCase()}|${packet.receiverUpiId.toLowerCase()}|${money(packet.amount)}`;
}

function transaction(id, fromUpiId, toUpiId, amount, type, note) {
  return {
    id,
    fromUpiId,
    toUpiId,
    amount,
    type,
    note,
    createdAt: new Date().toISOString()
  };
}

function register(name, mobile, upiId, pin) {
  const store = loadStore();
  const cleanUpi = normalizeUpi(upiId);
  if (!name.trim()) return fail("Name cannot be empty.");
  if (!/^\d{10}$/.test(mobile)) return fail("Mobile number must contain exactly 10 digits.");
  if (!/^[a-z0-9._-]{3,30}@[a-z]{3,20}$/.test(cleanUpi)) return fail("UPI ID format is invalid. Example: name@offline");
  if (!/^\d{4}$/.test(pin)) return fail("PIN must contain exactly 4 digits.");
  if (findUser(store, cleanUpi)) return fail("This UPI ID is already registered.");
  if (store.users.some(user => user.mobile === mobile)) return fail("This mobile number is already registered.");

  store.users.push({
    name: name.trim(),
    mobile,
    upiId: cleanUpi,
    pin,
    balance: 0,
    transactions: []
  });
  saveStore(store);
  return ok("Registration successful. You can login now.");
}

function login(upiId, pin) {
  const store = loadStore();
  const user = findUser(store, normalizeUpi(upiId));
  if (!user || user.pin !== pin) {
    return fail("Invalid UPI ID or PIN.");
  }
  state.currentUserUpi = user.upiId;
  return ok("Login successful.");
}

function addMoney(upiId, amount) {
  const store = loadStore();
  const user = findUser(store, upiId);
  if (!user) return fail("Account not found.");
  user.balance = Number(user.balance) + amount;
  user.transactions.push(transaction(makeId("TXN", 10), "CASH", user.upiId, amount, "CREDIT", "Demo money added"));
  saveStore(store);
  return ok("Money added successfully.");
}

function processPaymentPacket(packet) {
  const store = loadStore();
  const saved = store.receipts[packet.requestId];
  const fingerprint = packetFingerprint(packet);
  if (saved) {
    if (saved.fingerprint !== fingerprint) {
      return fail("Idempotency conflict: this packet request ID was already used for another payment.");
    }
    return {
      success: saved.success,
      message: `Duplicate packet detected. Returning saved result: ${saved.message}`
    };
  }

  const result = sendMoney(store, packet);
  store.receipts[packet.requestId] = {
    requestId: packet.requestId,
    fingerprint,
    success: result.success,
    message: result.message,
    createdAt: new Date().toISOString()
  };
  saveStore(store);
  return result;
}

function sendMoney(store, packet) {
  if (packet.senderUpiId.toLowerCase() === packet.receiverUpiId.toLowerCase()) {
    return fail("You cannot send money to your own UPI ID.");
  }
  const sender = findUser(store, packet.senderUpiId);
  const receiver = findUser(store, packet.receiverUpiId);
  if (!sender) return fail("Sender account not found.");
  if (!receiver) return fail("Receiver account not found.");
  if (sender.pin !== packet.pin) return fail("Incorrect UPI PIN.");
  if (Number(sender.balance) < packet.amount) return fail("Insufficient balance.");

  sender.balance = Number(sender.balance) - packet.amount;
  receiver.balance = Number(receiver.balance) + packet.amount;
  const txnId = makeId("TXN", 10);
  sender.transactions.push(transaction(
    txnId,
    sender.upiId,
    receiver.upiId,
    packet.amount,
    "DEBIT",
    `Paid to ${receiver.name} (${receiver.upiId})`
  ));
  receiver.transactions.push(transaction(
    txnId,
    sender.upiId,
    receiver.upiId,
    packet.amount,
    "CREDIT",
    `Received from ${sender.name} (${sender.upiId})`
  ));
  return ok(`Payment successful. Transaction ID: ${txnId}`);
}

function ok(message) {
  return { success: true, message };
}

function fail(message) {
  return { success: false, message };
}

function currentUser() {
  return findUser(loadStore(), state.currentUserUpi);
}

function refreshDashboard() {
  const user = currentUser();
  if (!user) return;
  elements.currentName.textContent = `Welcome, ${user.name}`;
  elements.currentUpi.textContent = user.upiId;
  elements.balanceText.textContent = `Rs. ${money(user.balance)}`;
  renderHistory(user);
  renderUsers();
}

function renderHistory(user) {
  const rows = [...user.transactions].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  if (!rows.length) {
    elements.historyBody.innerHTML = `<tr><td class="empty-row" colspan="5">No transactions yet.</td></tr>`;
    return;
  }
  elements.historyBody.innerHTML = rows.map(item => {
    const amountClass = item.type === "CREDIT" ? "amount-credit" : "amount-debit";
    return `
      <tr>
        <td>${escapeHtml(item.id)}</td>
        <td>${new Date(item.createdAt).toLocaleString()}</td>
        <td>${escapeHtml(item.type)}</td>
        <td class="${amountClass}">Rs. ${money(item.amount)}</td>
        <td>${escapeHtml(item.note)}</td>
      </tr>
    `;
  }).join("");
}

function renderUsers() {
  const store = loadStore();
  if (!store.users.length) {
    elements.usersBody.innerHTML = `<tr><td class="empty-row" colspan="4">No users registered.</td></tr>`;
    return;
  }
  elements.usersBody.innerHTML = store.users.map(user => `
    <tr>
      <td>${escapeHtml(user.name)}</td>
      <td>${escapeHtml(user.upiId)}</td>
      <td>${escapeHtml(user.mobile)}</td>
      <td>Rs. ${money(user.balance)}</td>
    </tr>
  `).join("");
}

function showDashboard() {
  elements.authView.classList.add("hidden");
  elements.dashboardView.classList.remove("hidden");
  refreshDashboard();
}

function showAuth() {
  elements.dashboardView.classList.add("hidden");
  elements.authView.classList.remove("hidden");
}

function showToast(message, type) {
  clearTimeout(state.toastTimer);
  elements.toast.textContent = message;
  elements.toast.className = `toast show ${type}`;
  state.toastTimer = setTimeout(() => {
    elements.toast.className = "toast";
  }, 3400);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#039;"
  })[char]);
}

document.querySelectorAll("[data-auth-tab]").forEach(button => {
  button.addEventListener("click", () => {
    document.querySelectorAll("[data-auth-tab]").forEach(tab => tab.classList.remove("active"));
    document.querySelectorAll(".auth-card .form").forEach(form => form.classList.remove("active"));
    button.classList.add("active");
    document.querySelector(`#${button.dataset.authTab}Form`).classList.add("active");
  });
});

document.querySelectorAll("[data-page]").forEach(button => {
  button.addEventListener("click", () => {
    document.querySelectorAll("[data-page]").forEach(item => item.classList.remove("active"));
    document.querySelectorAll(".page").forEach(page => page.classList.remove("active"));
    button.classList.add("active");
    document.querySelector(`#${button.dataset.page}`).classList.add("active");
    refreshDashboard();
  });
});

elements.loginForm.addEventListener("submit", event => {
  event.preventDefault();
  const result = login(elements.loginUpi.value, elements.loginPin.value);
  if (!result.success) {
    showToast(result.message, "error");
    return;
  }
  elements.loginPin.value = "";
  showToast(result.message, "success");
  showDashboard();
});

elements.registerForm.addEventListener("submit", event => {
  event.preventDefault();
  const result = register(
    elements.registerName.value,
    elements.registerMobile.value.trim(),
    elements.registerUpi.value,
    elements.registerPin.value
  );
  showToast(result.message, result.success ? "success" : "error");
  if (result.success) {
    elements.registerForm.reset();
  }
});

elements.walletForm.addEventListener("submit", event => {
  event.preventDefault();
  const amount = parseAmount(elements.walletAmount.value);
  if (amount === null) {
    showToast("Enter a valid amount greater than zero.", "error");
    return;
  }
  const result = addMoney(state.currentUserUpi, amount);
  elements.walletAmount.value = "";
  showToast(result.message, result.success ? "success" : "error");
  refreshDashboard();
});

elements.paymentForm.addEventListener("submit", event => {
  event.preventDefault();
  const amount = parseAmount(elements.paymentAmount.value);
  if (amount === null) {
    showToast("Enter a valid amount greater than zero.", "error");
    return;
  }
  const requestId = elements.packetId.value.trim() || makeId("REQ", 12);
  elements.packetId.value = requestId;
  const packet = {
    requestId,
    senderUpiId: state.currentUserUpi,
    receiverUpiId: normalizeUpi(elements.receiverUpi.value),
    amount,
    pin: elements.paymentPin.value.trim()
  };
  state.lastPacket = packet;
  elements.lastPacketText.textContent = requestId;
  const result = processPaymentPacket(packet);
  elements.paymentPin.value = "";
  showToast(result.message, result.success ? "success" : "error");
  refreshDashboard();
});

elements.retryButton.addEventListener("click", () => {
  if (!state.lastPacket) {
    showToast("No packet has been sent in this session.", "error");
    return;
  }
  const result = processPaymentPacket(state.lastPacket);
  showToast(result.message, result.success ? "success" : "error");
  refreshDashboard();
});

elements.generatePacketButton.addEventListener("click", () => {
  const requestId = makeId("REQ", 12);
  elements.packetId.value = requestId;
  elements.heroPacket.textContent = requestId;
});

elements.logoutButton.addEventListener("click", () => {
  state.currentUserUpi = null;
  state.lastPacket = null;
  elements.lastPacketText.textContent = "None yet";
  showAuth();
});

elements.heroPacket.textContent = makeId("REQ", 8);
