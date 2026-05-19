#Projeto Halo

##O que é o projeto
O projeto Halo é uma aplicativo para controle dos gastos pessoas, seu objetivo é realizar o registro de todos os gastos feitos pelo usuário. 
O usuário manda mensagens com seu gastos e a aplicação faz o registro. E no final gerar relatórios e um dashboard para acompanhamento.
Para não ser necessário instalação de aplicativo o registro é feito pelo whatsapp da pessoa, através de instegração com api Evolution GO.

##Stack
Monorepo

###Backend
Spring Boot API, Postgresql, Evolution GO (Documentação: https://evapi.debatin.dev/swagger/index.html), Germini Ai integração

###Frontend
React, Tailwind

##Brainstorm
- Usuário registra seu gasto mandando mensagem com seu gasto (Compra na loja X, valor 55,50) a aplicação recebe a informação através da integração com a evelution Go, envia para a 
ia realizar a categorização da compra e registra no banco.
- Usuário pode informar a data da campra ou não informar e o sistema usar a data atual.
- Usuário pode acessar a aplicação web para visualizar seus registro, realizar alterações e registro manual.
- Usuário pode editar as categorias de gastos através da aplicação web.
- O cadastro na aplicação é feito através do whatsapp, ao receber uma mensagem a aplicação verifica se já está cadastrada, se não solicita apenas o nome e cadastra o usuário com nome e 
deu telefone.
- Para acessar a aplicação web não tem usuário e senha, o usuário informa seu telefone, recebe um código no whatsapp e faz o login.
- Caso o usuário não saiba o endereço da aplicação web ele pode solictar pelo próprio whatsapp.
- O usuário pode solicitar pelo whatsapp um resumos dos seus gastos no mês atual ou anteriores, e reber por mensagem uma tabela com um gráfico (pode ser gerada por ia).
- A aplicação web deve ser focado para acesso mobile.
