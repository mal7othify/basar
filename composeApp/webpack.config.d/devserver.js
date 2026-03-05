const fs = require('fs');
const path = require('path');

config.devServer = {
    ...config.devServer,
    host: '0.0.0.0',
    port: 3000,
    server: {
        type: 'https',
        options: {
            key: fs.readFileSync(path.join(__dirname, '../../../../localhost-key.pem')),
            cert: fs.readFileSync(path.join(__dirname, '../../../../localhost-cert.pem')),
        }
    },
    allowedHosts: 'all',
    hot: true,
    liveReload: true,
};