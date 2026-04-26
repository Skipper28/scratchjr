module.exports = {
    devtool: 'source-map',
    entry: {
        app: './src/entry/app.js'
    },
    output: {
        path: __dirname + '/src/build/bundles',
        filename: '[name].bundle.js'
    },
    performance: {
        hints: false
    },
    watchOptions: {
        ignored: ['node_modules', 'src/build/**/*']
    },
    module: {
        rules: [
            {
                test: /\.jsx?$/,
                exclude: /node_modules/,
                loader: 'babel-loader',
                options: {
                    presets: ['@babel/preset-env']
                }
            }
        ]
    }
};
