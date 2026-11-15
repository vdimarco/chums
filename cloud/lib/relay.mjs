// Artifact previews are bytes; JSON responses retain the existing API contract.
export async function relay(upstream, res) {
  const type = upstream.headers.get('content-type')?.split(';')[0] || 'application/json'
  if (!['image/jpeg', 'image/png'].includes(type)) return res.status(upstream.status).json(await upstream.json())
  if (Number(upstream.headers.get('content-length') || 0) > 2 * 1024 * 1024) throw Error('Preview too large')
  const chunks = []; let size = 0
  for await (const chunk of upstream.body) {
    size += chunk.length
    if (size > 2 * 1024 * 1024) throw Error('Preview too large')
    chunks.push(Buffer.from(chunk))
  }
  res.setHeader('Content-Type', type)
  res.setHeader('Cache-Control', 'private, no-store')
  return res.status(upstream.status).send(Buffer.concat(chunks))
}
