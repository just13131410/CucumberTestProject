# Angular-Vorgangs-Dashboard (GUIProjekt) als statisches Testziel für die @GUI-Szenarien.
# Build-Kontext: Inhalt von GUIProjekt/guiprojekt/dist/guiprojekt/browser (nach `ng build`)
# plus diese Dockerfile und nginx.conf (siehe k8s/README.md, Abschnitt Remote-Browser).
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY browser/ /usr/share/nginx/html/
EXPOSE 8080
