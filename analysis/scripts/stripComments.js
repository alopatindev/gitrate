#!/usr/bin/env node

const fs = require('fs')
const decomment = require('decomment')

const filename = process.argv.pop()
const text = fs.readFileSync(filename, 'utf8')

const strippedText = decomment.text(text)
fs.writeFileSync(filename, strippedText)
