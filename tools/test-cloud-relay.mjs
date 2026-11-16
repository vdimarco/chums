import assert from 'node:assert/strict'
import { relay } from '../cloud/lib/relay.mjs'
const response = () => ({ headers:{}, status(code) { this.code=code; return this }, setHeader(k,v) { this.headers[k]=v }, json(body) { this.body=body }, send(body) { this.body=body } })
const json = response()
await relay(new Response('{"error":"denied"}',{status:401,headers:{'content-type':'application/json'}}),json)
assert.equal(json.code,401); assert.equal(json.body.error,'denied')
const image = response(), bytes=Buffer.from([0,255,128,42])
await relay(new Response(bytes,{headers:{'content-type':'image/jpeg'}}),image)
assert.deepEqual(image.body,bytes); assert.equal(image.headers['Content-Type'],'image/jpeg')
await assert.rejects(relay(new Response(Buffer.alloc(2*1024*1024+1),{headers:{'content-type':'image/png'}}),response()),/too large/)
console.log('Cloud JSON and binary preview relay checks passed')
