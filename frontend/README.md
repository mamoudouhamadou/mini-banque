# Mini-banque : interface Angular

Interface web de la mini-banque : **Angular 21**, composants *standalone*, **signals**, mode *zoneless*, Reactive Forms et Vitest.

```bash
npm install
npm start        # http://localhost:4200 ; /api est relayé vers l'API (voir proxy.conf.json)
npm test         # tests unitaires (Vitest)
npm run build    # version de production dans dist/
```

L'API Spring Boot doit tourner sur le port indiqué dans `proxy.conf.json` (8082 par défaut). Voir le README à la racine du dépôt.
