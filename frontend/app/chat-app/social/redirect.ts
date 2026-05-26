import { redirect } from 'next/navigation';

export function redirectToPersonaFeed() {
    redirect('/chat-app/persona');
}

export function redirectToPersonaProfile(personaId: string) {
    redirect(`/chat-app/persona/${personaId}`);
}
