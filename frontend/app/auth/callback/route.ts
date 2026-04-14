import { NextResponse } from 'next/server'
import { createSupabaseServer } from '@/lib/supabase/server'

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url)
  const code = searchParams.get('code')
  // if "next" is in param, use it as the redirect URL
  const next = searchParams.get('next') ?? '/'

  // Derive the public-facing origin:
  // 1. Use NEXT_PUBLIC_SITE_URL if baked in at build time (production/staging)
  // 2. Fall back to X-Forwarded-Host + X-Forwarded-Proto headers set by nginx
  // 3. Last resort: origin from request.url (may be an internal ACA hostname)
  const siteUrl = process.env.NEXT_PUBLIC_SITE_URL
  const forwardedHost = request.headers.get('x-forwarded-host')
  const forwardedProto = request.headers.get('x-forwarded-proto') ?? 'https'
  const origin =
    siteUrl ||
    (forwardedHost ? `${forwardedProto}://${forwardedHost}` : new URL(request.url).origin)

  if (code) {
    const supabase = await createSupabaseServer()
    const { error } = await supabase.auth.exchangeCodeForSession(code)

    if (!error) {
      // Successful login, redirect to the desired route
      return NextResponse.redirect(`${origin}${next}`)
    } else {
      console.error('Exchange code error:', error)
    }
  }

  // return the user to an error page with instructions or simply back to login
  return NextResponse.redirect(`${origin}/login?error=auth-code-exchange`)
}
