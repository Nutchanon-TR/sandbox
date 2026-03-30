import { NextResponse } from 'next/server'
import { createSupabaseServer } from '@/lib/supabase/server'

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url)
  const code = searchParams.get('code')
  // if "next" is in param, use it as the redirect URL
  const next = searchParams.get('next') ?? '/'

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
