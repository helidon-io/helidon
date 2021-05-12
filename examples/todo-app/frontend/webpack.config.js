/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* global __dirname, Infinity */

const global = require('./src/main/webapp/js/global.js');
const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const UglifyJsPlugin = require('uglifyjs-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const OptimizeCSSPlugin = require('optimize-css-assets-webpack-plugin');

function resolve (dir) {
  return path.join(__dirname, dir);
}

module.exports = {
    entry: './src/main/webapp/js/main.js',
    output: {
        path: resolve('/target/classes/WEB/'),
        filename: 'js/[name].[chunkhash].js',
        chunkFilename: 'js/[id].[chunkhash].js'
    },
    module: {
        rules: [
            {
                test: /\.html$/,
                loader: 'html-loader'
            },
            {
                test: /\.css$/,
                use: ExtractTextPlugin.extract({
                    use: [{loader: 'css-loader', options: {sourceMap: false}}]
                })
            }
        ]
    },
    resolve: {
        alias: {
          'jquery': resolve('node_modules/jquery/dist/jquery.js'),
          'knockout': resolve('node_modules/knockout/build/output/knockout-latest.js'),
          'todomvc-common': resolve('node_modules/todomvc-common/base.js'),
          'todomvc-common-css': resolve('node_modules/todomvc-common/base.css'),
          'todomvc-app-css': resolve('node_modules/todomvc-app-css/index.css'),
          '@': resolve('src/main/webapp/js')
        },
        extensions: ['.js', '.css'] // File types
    },
    plugins: [
        new ExtractTextPlugin({
            filename: 'css/[name].[contenthash].css',
            allChunks: true
        }),
        new OptimizeCSSPlugin({
            cssProcessorOptions: {safe: true, map: {inline: false}}
        }),
        new UglifyJsPlugin({
          uglifyOptions: { compress: { warnings: false } },
          sourceMap: true,
          parallel: true
        }),
        new HtmlWebpackPlugin({
            filename: resolve('target/classes/WEB/index.html'),
            template: 'src/main/webapp/index.html',
            'meta': {
               'google-signin-client_id': global.GOOGLE_SIGN_IN_CLIENT_ID
            },
            inject: true,
            minify: {
                removeComments: true,
                collapseWhitespace: true,
                removeAttributeQuotes: true
            },
            chunksSortMode: 'dependency'
        }),
        new webpack.HashedModuleIdsPlugin(),
        new webpack.optimize.ModuleConcatenationPlugin(),
        new webpack.optimize.CommonsChunkPlugin({
            name: 'vendor',
            minChunks(module) {
                return (
                        module.resource &&
                        /\.js$/.test(module.resource) &&
                        module.resource.indexOf(
                                path.join(__dirname, './node_modules')
                                ) === 0
                        );
            }
        }),
        new webpack.optimize.CommonsChunkPlugin({
            name: 'manifest',
            minChunks: Infinity
        }),
        new webpack.optimize.CommonsChunkPlugin({
            name: 'app',
            async: 'vendor-async',
            children: true,
            minChunks: 3
        })
    ]
};
