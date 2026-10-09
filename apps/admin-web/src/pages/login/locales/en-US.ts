export default {
  kicker: 'Admin Console',
  title: 'Admin Sign In',
  description: 'Sign in with your admin account to continue.',
  fields: {
    username: 'Username',
    password: 'Password',
    rememberUsername: 'Remember username',
  },
  actions: {
    submit: 'Sign in',
    submitting: 'Signing in…',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    clearUsername: 'Clear username',
    clearPassword: 'Clear password',
  },
  errors: {
    usernameRequired: 'Enter a username',
    passwordRequired: 'Enter a password',
    generic: 'Sign in failed. Please try again.',
    profileLoadFailed: 'Signed in, but loading your profile failed. Ensure the admin service is running.',
  },
} as const
