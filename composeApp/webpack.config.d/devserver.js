config.devServer = {
    ...config.devServer,
    host: '0.0.0.0',  // Allow external connections
    port: 8080,
    https: true,      // Enable HTTPS (required for WebXR)
    allowedHosts: 'all',
};