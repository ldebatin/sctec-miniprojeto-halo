# Stage 1: Build a aplicação React
FROM node:20-alpine AS build

WORKDIR /app

# Copia package.json e package-lock.json
COPY package*.json ./

# Instala as dependências
RUN npm ci

# Copia o resto do código da aplicação
COPY . .

# Define o build argument para a URL da API
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Builda a aplicação para produção
RUN npm run build

# Stage 2: Serve a aplicação com Nginx
FROM nginx:1.27-alpine AS final

# Copia os arquivos buildados do estágio de build
COPY --from=build /app/dist /usr/share/nginx/html

# Remove a configuração padrão do Nginx
RUN rm /etc/nginx/conf.d/default.conf

# Copia a nova configuração do Nginx
COPY nginx.conf /etc/nginx/conf.d

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
