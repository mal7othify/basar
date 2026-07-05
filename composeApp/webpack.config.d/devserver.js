const fs = require('fs');
const path = require('path');

// WebXR requires a secure context, so local development serves over HTTPS
// with a locally generated certificate (untracked; see README). This file is
// evaluated on every webpack invocation — including production builds on CI,
// where the certificate does not exist — so HTTPS is applied only when the
// certificate files are present.
const keyPath = path.join(__dirname, '../../../../localhost-key.pem');
const certPath = path.join(__dirname, '../../../../localhost-cert.pem');

config.devServer = {
    ...config.devServer,
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: 'all',
    hot: true,
    liveReload: true,
};

if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
    config.devServer.server = {
        type: 'https',
        options: {
            key: fs.readFileSync(keyPath),
            cert: fs.readFileSync(certPath),
        },
    };
}
