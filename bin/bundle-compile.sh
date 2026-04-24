#!/bin/sh
set -e
cd ..
export NODE_OPTIONS="--openssl-legacy-provider"
node ./node_modules/webpack/bin/webpack.js --mode=production
