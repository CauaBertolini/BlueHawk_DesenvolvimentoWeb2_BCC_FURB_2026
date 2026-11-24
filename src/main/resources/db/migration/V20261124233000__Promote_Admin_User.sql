-- Torna ADMIN (role = 1) a conta do Cauã, se ela já existir. Não cria conta nem define senha: o repositório é
-- público, então nenhum hash de senha fica aqui. A conta é criada antes, pelo cadastro normal do site.
--
-- Uma migração versionada roda uma vez só: se a conta ainda não existir quando ela rodar, nada muda e é preciso
-- promover à mão (UPDATE "user" SET role = 1 WHERE email = '...').
UPDATE "user"
SET role = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE LOWER(TRIM(email)) = 'cauabertolini@gmail.com'
  AND role <> 1;
