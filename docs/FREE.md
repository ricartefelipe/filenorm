# Demo gratuita (sem VPS, domínio, Stripe ou SMTP)

```bash
./scripts/free-demo.sh
```

- Local: `http://localhost:9081`
- HTTPS público: URL `*.trycloudflare.com` (temporária)
- Login: informe o **código de acesso da demo** (impresso pelo script e salvo em `.demo-access-code`) para o magic link aparecer na tela. Sem o código, o link vai só para o Mailpit local, e quem tiver a URL pública não consegue entrar como outra pessoa.
- Não envie extratos reais: quem tem o código entra com qualquer e-mail.
- Mailpit: `http://localhost:18026`

O PC precisa ficar ligado. A URL do túnel muda a cada restart.
