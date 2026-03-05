const https = require('https');
const fs = require('fs');
const path = require('path');
const express = require('express');

const app = express();

//Serve static files from the webpack output and resources
app.use(express.static('composeApp/build/processedResources/js/main'));
app.use(express.static('composeApp/build/kotlin-webpack/js/developmentExecutable'));

// For root route, serve the index.html
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'composeApp/build/processedResources/js/main/index.html'));
});

// Create HTTPS server with self-signed certificate
const options = {
    key: fs.readFileSync(path.join(__dirname, 'localhost-key.pem'), 'utf8'),
    cert: fs.readFileSync(path.join(__dirname, 'localhost-cert.pem'), 'utf8')
};

const server = https.createServer(options, app);

const PORT = 3000;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 HTTPS Server running at:`);
    console.log(`   https://localhost:${PORT}`);
    console.log(`   https://0.0.0.0:${PORT} (accessible from network)`);
    console.log('');
    console.log('📱 To test on Android:');
    console.log('   1. Find your local IP: ifconfig | grep "inet "');
    console.log('   2. Open https://YOUR_IP:3000 on your Android device');
    console.log('   3. Accept the self-signed certificate warning');
});