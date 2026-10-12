const KEY = 'finguard_device_id'

/**
 * A random per-browser id sent as X-Device-Id for the NEW_DEVICE fraud rule. It is not a
 * secret and is client-controlled by nature; the fraud engine treats a missing id as risky.
 */
export function getDeviceId(): string {
  try {
    let id = localStorage.getItem(KEY)
    if (!id) {
      id = crypto.randomUUID()
      localStorage.setItem(KEY, id)
    }
    return id
  } catch {
    return 'storage-unavailable'
  }
}
