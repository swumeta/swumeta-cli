// Function to check if consent already exists
function checkCookieConsent() {
    return localStorage.getItem('cookieConsent');
}

// Function to set cookie consent
function setCookieConsent(preferences) {
    localStorage.setItem('cookieConsent', JSON.stringify(preferences));

    // If user accepted analytics cookies, load Google Analytics
    if (preferences.analytics) {
        loadGoogleAnalytics();
    }

    document.getElementById('cookieBanner').classList.remove('show');
}

// Function to load Google Analytics (example)
function loadGoogleAnalytics() {
    // Code to load Google Analytics
    console.log("Google Analytics loaded");

    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());
    gtag('config', 'G-3LZG44YH87');

    // Create script tag for GA
    const script = document.createElement('script');
    script.src = 'https://www.googletagmanager.com/gtag/js?id=G-3LZG44YH87';
    script.async = true;
    document.head.appendChild(script);
}

// On page load
document.addEventListener('DOMContentLoaded', function() {
    const consentData = checkCookieConsent();

    // If consent already exists, hide banner and load appropriate services
    if (consentData) {
        const preferences = JSON.parse(consentData);

        if (preferences.analytics) {
            loadGoogleAnalytics();
        }
        document.getElementById('cookieBanner').classList.add('cookie-banner-hidden');
    } else {
        // Only show banner if no consent exists
        document.getElementById('cookieBanner').classList.add('show');
    }

    // Handle click on "Accept all cookies"
    document.getElementById('acceptAll').addEventListener('click', function() {
        const preferences = {
            necessary: true,
            analytics: true,
            marketing: true
        };
        setCookieConsent(preferences);
    });

    // Handle click on "Accept necessary cookies only"
    document.getElementById('acceptNecessary').addEventListener('click', function() {
        const preferences = {
            necessary: true,
            analytics: false,
            marketing: false
        };
        setCookieConsent(preferences);
    });

    // Handle click on "Customize my choices"
    document.getElementById('customizeBtn').addEventListener('click', function() {
        const settingsPanel = document.getElementById('cookieSettings');
        if (settingsPanel.style.display === 'none') {
            settingsPanel.style.display = 'block';
        } else {
            settingsPanel.style.display = 'none';
        }
    });

    // Handle click on "Save my preferences"
    document.getElementById('savePreferences').addEventListener('click', function() {
        const preferences = {
            necessary: true, // Always required
            analytics: document.getElementById('analyticsCookies').checked,
            marketing: document.getElementById('marketingCookies').checked
        };
        setCookieConsent(preferences);
    });
});
