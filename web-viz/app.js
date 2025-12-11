// ---------------------------------------------------
// Configuration
// ---------------------------------------------------

const API = "http://localhost:8081/stock-api";

// ---------------------------------------------------
// Element References
// ---------------------------------------------------

const els = {
    symbolSelect: document.getElementById("symbolSelect"),
    refreshBtn: document.getElementById("refreshBtn"),
    status: document.getElementById("status"),
    // the KPI + chart ones can stay or be deleted, they’re just unused now
    kpiClose: document.getElementById("kpiClose"),
    kpi5d: document.getElementById("kpi5d"),
    kpiYtd: document.getElementById("kpiYtd"),
    lineCanvas: document.getElementById("lineChart"),
    barCanvas: document.getElementById("barChart"),
    donutCanvas: document.getElementById("donutChart"),

    // markets today
    mtMarket: document.getElementById("mtMarket"),
    mtLeader: document.getElementById("mtLeader"),
    mtTopName: document.getElementById("mtTopName"),
    mtTopPct: document.getElementById("mtTopPct"),
    mtWorstName: document.getElementById("mtWorstName"),
    mtWorstPct: document.getElementById("mtWorstPct"),

    // tables
    marketSummaryBody: document.getElementById("marketSummaryBody"),
    gainersBody: document.getElementById("gainersBody"),
    losersBody: document.getElementById("losersBody"),
};

// ---------------------------------------------------
// Status Helper
// ---------------------------------------------------

function setStatus(msg, isError = false) {
    if (!msg) {
        els.status.style.display = "none";
    } else {
        els.status.style.display = "block";
        els.status.textContent = msg;
        if (isError) els.status.classList.add("error");
        else els.status.classList.remove("error");
    }
}

// ---------------------------------------------------
// Load Markets Today
// ---------------------------------------------------

async function loadMarketsToday() {
    if (!els.mtMarket) return;

    try {
        const res = await fetch("http://localhost:8081/stock-api/info");
        if (!res.ok) throw new Error(`info fetch failed: ${res.status}`);
        const d = await res.json();

        els.mtMarket.textContent = d.market ?? "-";
        els.mtLeader.textContent = d.leader ?? "-";

        // top stock
        els.mtTopName.textContent = d.topStock ?? "-";
        stylePercent(els.mtTopPct, d.topStockPercentage);

        // worst stock
        els.mtWorstName.textContent = d.worstStock ?? "-";
        stylePercent(els.mtWorstPct, d.worstStockPercentage);

    } catch (e) {
        console.error(e);
    }
}

function stylePercent(el, pct) {
    if (!pct) {
        el.textContent = "-";
        el.className = "mt-percent";
        return;
    }

    el.className = "mt-percent";

    const val = parseFloat(pct);

    if (val > 0) {
        el.classList.add("green");
        el.textContent = `${pct} ↑`; // DO NOT ADD %
    } else {
        el.classList.add("red");
        el.textContent = `${pct} ↓`;
    }
}

// helper to color + arrow
function stylePercent(el, pct) {
    if (!pct || pct === "-") {
        el.className = "mt-percent";
        return;
    }

    const value = parseFloat(pct);

    if (value > 0) {
        el.className = "mt-percent green";
        el.textContent = `${pct} ↑`;
    } else {
        el.className = "mt-percent red";
        el.textContent = `${pct} ↓`;
    }
}

// ---------------------------------------------------
// Load Market Summary
// ---------------------------------------------------

async function loadMarketSummary() {
    try {
        const res = await fetch("http://localhost:8081/stock-api/market-summary");
        if (!res.ok) throw new Error("Failed to load market summary");

        const data = await res.json();
        els.marketSummaryBody.innerHTML = "";

        data.forEach(item => {
            const symbol = item.companyName;
            const price = item.todayPrice;
            const change = item.priceChange;
            const pct = item.change;

            const logo = logos[symbol] || "";

            // detect positive or negative
            const isPositive = parseFloat(pct) > 0;
            const pillClass = isPositive ? "change-green" : "change-red";

            const tr = document.createElement("tr");
            tr.classList.add("market-summary-row");

            tr.innerHTML = `
                <td>
                    <div class="symbol-cell">
                        ${logo ? `<img src="${logo}" class="symbol-logo"/>` : ""}
                        ${symbol}
                    </div>
                </td>
                <td>${price}</td>
                <td>${change}</td>
                <td>
                    <span class="change-pill ${pillClass}">${pct}</span>
                </td>
            `;

            els.marketSummaryBody.appendChild(tr);
        });

    } catch (err) {
        console.error(err);
        setStatus("Failed to load Market Summary", true);
    }
}

// ---------------------------------------------------
// Load Active Stocks
// ---------------------------------------------------

function formatNum(n) {
    if (n === null || n === undefined || n === "null") return "-";
    return Number(n).toFixed(2);
}

function formatVolume(v) {
    if (!v || v === "null") return "-";
    if (v >= 1_000_000_000) return (v / 1_000_000_000).toFixed(2) + "B";
    if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + "M";
    if (v >= 1_000) return (v / 1_000).toFixed(2) + "K";
    return v;
}

function formatPercent(p) {
    if (!p) return "-";
    const value = parseFloat(p);
    const className = value > 0 ? "change-green" : "change-red";
    return `<span class="change-pill ${className}">${value.toFixed(2)}%</span>`;
}

function getCompany(i) {
    return i.companyName || "-";
}

function cleanNumber(n) {
    if (!n) return "-";
    return Number(String(n).replace("$", "")).toFixed(2);
}

function formatPercent(p) {
    if (!p) return "-";
    const num = parseFloat(p);
    const cls = num >= 0 ? "change-green" : "change-red";
    return `<span class="change-pill ${cls}">${num.toFixed(2)}%</span>`;
}

function renderRow(i) {
    const symbol = i.companyName || "-";
    const logo = logos[symbol] || "";

    const price = i.price != null ? i.price.toFixed(2) : "-";
    const change = i.change != null ? i.change.toFixed(2) : "-";

    // percent pill
    const pctNum = parseFloat(i.changePercent);
    const cls = pctNum >= 0 ? "change-green" : "change-red";
    const pct = isNaN(pctNum)
        ? "-"
        : `<span class="change-pill ${cls}">${pctNum.toFixed(2)}%</span>`;

    const volume = i.volume ?? "-";
    const rvol = i.rvol ?? "-";
    const floatVal = i.float ?? "-";
    const marketCap = i.marketCap ?? "-";

    return `
        <tr class="active-row">
            <td>
                <div class="symbol-cell">
                    ${logo ? `<img src="${logo}" class="symbol-logo" />` : ""}
                    <span>${symbol}</span>
                </div>
            </td>
            <td>${price}</td>
            <td>${change}</td>
            <td>${pct}</td>
            <td>${volume}</td>
        </tr>
    `;
}

async function loadActiveStocks() {
    try {
        const res = await fetch("http://localhost:8081/stock-api/active-stocks");
        const { biggestGainers, biggestLosers } = await res.json();

        els.gainersBody.innerHTML = "";
        els.losersBody.innerHTML = "";

        biggestGainers.forEach(item => {
            els.gainersBody.innerHTML += renderRow(item);
        });

        biggestLosers.forEach(item => {
            els.losersBody.innerHTML += renderRow(item);
        });

    } catch (err) {
        console.error(err);
    }
}

function stylePercent(el, pct) {
    if (!pct) {
        el.textContent = "-";
        el.className = "mt-percent";
        return;
    }

    const val = parseFloat(pct);
    el.className = "mt-percent"; // reset base class

    if (val > 0) {
        el.classList.add("green");
        el.textContent = `${pct} ↑`;   // backend already includes %
    } else {
        el.classList.add("red");
        el.textContent = `${pct} ↓`;
    }
}

// ---------------------------------------------------
// Refresh All
// ---------------------------------------------------

async function refreshAll() {
    setStatus("Loading...");

    await Promise.all([
        loadMarketsToday(),
        loadMarketSummary(),
        loadActiveStocks()
    ]);

    setStatus(""); // clear once done
}

// ---------------------------------------------------
// Event Listeners
// ---------------------------------------------------

els.refreshBtn.addEventListener("click", refreshAll);

// Initial Page Load
document.addEventListener("DOMContentLoaded", refreshAll);







const logos = {
    AAPL: "https://logo.clearbit.com/apple.com",
    AMZN: "https://logo.clearbit.com/amazon.com",
    MSFT: "https://logo.clearbit.com/microsoft.com",
    GOOGL: "https://logo.clearbit.com/abc.xyz",
    GOOG: "https://logo.clearbit.com/abc.xyz",
    META: "https://logo.clearbit.com/meta.com",
    TSLA: "https://logo.clearbit.com/tesla.com",
    NVDA: "https://logo.clearbit.com/nvidia.com",
    NFLX: "https://logo.clearbit.com/netflix.com",
    AMD: "https://logo.clearbit.com/amd.com",
    INTC: "https://logo.clearbit.com/intel.com",
    IBM: "https://logo.clearbit.com/ibm.com",
    ORCL: "https://logo.clearbit.com/oracle.com",
    CRM: "https://logo.clearbit.com/salesforce.com",
    ABNB: "https://logo.clearbit.com/airbnb.com",
    BABA: "https://logo.clearbit.com/alibaba.com",
    BAC: "https://logo.clearbit.com/bankofamerica.com",
    BA: "https://logo.clearbit.com/boeing.com",
    CAT: "https://logo.clearbit.com/caterpillar.com",
    CSCO: "https://logo.clearbit.com/cisco.com",
    CMCSA: "https://logo.clearbit.com/comcast.com",
    C: "https://logo.clearbit.com/citigroup.com",
    COST: "https://logo.clearbit.com/costco.com",
    CVS: "https://logo.clearbit.com/cvshealth.com",
    CVX: "https://logo.clearbit.com/chevron.com",
    DIS: "https://logo.clearbit.com/thewaltdisneycompany.com",
    DAL: "https://logo.clearbit.com/delta.com",
    DE: "https://logo.clearbit.com/deere.com",
    DELL: "https://logo.clearbit.com/dell.com",
    DOCU: "https://logo.clearbit.com/docusign.com",
    EBAY: "https://logo.clearbit.com/ebay.com",
    ECL: "https://logo.clearbit.com/ecolab.com",
    EMR: "https://logo.clearbit.com/emerson.com",
    EXPE: "https://logo.clearbit.com/expediagroup.com",
    F: "https://logo.clearbit.com/ford.com",
    GM: "https://logo.clearbit.com/gm.com",
    GE: "https://logo.clearbit.com/ge.com",
    GS: "https://logo.clearbit.com/goldmansachs.com",
    HD: "https://logo.clearbit.com/homedepot.com",
    HON: "https://logo.clearbit.com/honeywell.com",
    ISRG: "https://logo.clearbit.com/intuitive.com",
    JNJ: "https://logo.clearbit.com/jnj.com",
    JPM: "https://logo.clearbit.com/jpmorganchase.com",
    KO: "https://logo.clearbit.com/coca-colacompany.com",
    KHC: "https://logo.clearbit.com/kraftheinzcompany.com",
    LLY: "https://logo.clearbit.com/lilly.com",
    LMT: "https://logo.clearbit.com/lockheedmartin.com",
    MA: "https://logo.clearbit.com/mastercard.com",
    MCD: "https://logo.clearbit.com/mcdonalds.com",
    MDT: "https://logo.clearbit.com/medtronic.com",
    MMM: "https://logo.clearbit.com/3m.com",
    MO: "https://logo.clearbit.com/altria.com",
    MRK: "https://logo.clearbit.com/merck.com",
    NKE: "https://logo.clearbit.com/nike.com",
    NOC: "https://logo.clearbit.com/northropgrumman.com",
    NEM: "https://logo.clearbit.com/newmont.com",
    NEP: "https://logo.clearbit.com/nexteraenergy.com",
    NTES: "https://logo.clearbit.com/neteasegames.com",
    PANW: "https://logo.clearbit.com/paloaltonetworks.com",
    PEP: "https://logo.clearbit.com/pepsico.com",
    PFE: "https://logo.clearbit.com/pfizer.com",
    PG: "https://logo.clearbit.com/pg.com",
    PLTR: "https://logo.clearbit.com/palantir.com",
    PYPL: "https://logo.clearbit.com/paypal.com",
    QCOM: "https://logo.clearbit.com/qualcomm.com",
    RBLX: "https://logo.clearbit.com/roblox.com",
    RIVN: "https://logo.clearbit.com/rivian.com",
    RTX: "https://logo.clearbit.com/rtx.com",
    SBUX: "https://logo.clearbit.com/starbucks.com",
    SHOP: "https://logo.clearbit.com/shopify.com",
    SNAP: "https://logo.clearbit.com/snap.com",
    SONY: "https://logo.clearbit.com/sony.com",
    SPOT: "https://logo.clearbit.com/spotify.com",
    SQ: "https://logo.clearbit.com/block.xyz",
    T: "https://logo.clearbit.com/att.com",
    TM: "https://logo.clearbit.com/toyota.com",
    TSM: "https://logo.clearbit.com/tsmc.com",
    UBER: "https://logo.clearbit.com/uber.com",
    UNH: "https://logo.clearbit.com/unitedhealthgroup.com",
    UPS: "https://logo.clearbit.com/ups.com",
    V: "https://logo.clearbit.com/visa.com",
    VZ: "https://logo.clearbit.com/verizon.com",
    WMT: "https://logo.clearbit.com/walmart.com",
    WFC: "https://logo.clearbit.com/wellsfargo.com",
    XOM: "https://logo.clearbit.com/exxonmobil.com",
    ZM: "https://logo.clearbit.com/zoom.us",
    ZS: "https://logo.clearbit.com/zscaler.com",
};
