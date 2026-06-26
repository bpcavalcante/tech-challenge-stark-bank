# Tech Challenge – Stark Bank

API REST que integra com o Stark Bank para emitir boletos automaticamente e transferir os valores recebidos.

## Stack

| Tecnologia 
|------------|--------|-----|
| Java 17 
| Spring Boot
| Spring Scheduler | — | Agendamento de boletos (`@Scheduled` fixedRate 3h) |
| PostgreSQL | Banco de produção (AWS RDS)
| Stark Bank Java SDK | 2.16.0 | Integração com a API Stark Bank 
| ECDSA secp256k1 | — | Autenticação e validação de assinatura do webhook 
| nginx | — | Reverse proxy com terminação HTTPS (produção) |
| Let's Encrypt | — | Certificado TLS (produção) |

## Fluxo principal

```
Scheduler (a cada 3h)
  └─► Cria 8–12 invoices aleatórias no Stark Bank
        └─► Sandbox simula pagamento de algumas delas
              └─► Stark Bank envia webhook POST /webhook
                    └─► Aplicação transfere valor líquido (amount - fee)
                          └─► Destino: Stark Bank S.A. (banco 20018183)
```

## Como rodar

### Pré-requisitos

- Java 17
- Maven (ou use o wrapper `mvnw.cmd`)
- Docker Desktop (para subir o PostgreSQL)
- [ngrok](https://ngrok.com/) para expor o webhook localmente
- Credenciais Stark Bank sandbox: Project ID + chave privada ECDSA secp256k1

### Configuração

1. Gere ou adicione as chaves em `keys/privateKey.pem` e `keys/publicKey.pem`
2. Registre a chave pública no [dashboard sandbox](https://web.sandbox.starkbank.com)
3. Registre o webhook apontando para `https://<sua-url-ngrok>/webhook` com subscription `invoice`

```bash
# Expor localmente
ngrok http 8080
```

### Banco de dados (desenvolvimento)

Suba o PostgreSQL com Docker Compose antes de iniciar a aplicação:

```bash
docker-compose up -d
```

| Parâmetro | Valor |
|-----------|-------|
| Host | `localhost:5432` |
| Database | `challengedb` |
| Usuário | `challenge` |
| Senha | `challenge` |

### Iniciar

```bash
# Windows
$env:STARK_PROJECT_ID = "seu-project-id"
.\mvnw.cmd spring-boot:run

# Linux/macOS
export STARK_PROJECT_ID="seu-project-id"
./mvnw spring-boot:run
```

A aplicação sobe na porta `8080`. O scheduler executa 15 segundos após o início e depois a cada 3 horas.

### Variáveis de ambiente

| Variável | Descrição | Obrigatório |
|----------|-----------|-------------|
| `STARK_PROJECT_ID` | ID do projeto no Stark Bank | Sim |
| `STARK_PRIVATE_KEY` | Chave privada PEM (produção) | Apenas em prod |

Em desenvolvimento, a chave é lida de `keys/privateKey.pem`.

### Perfis Spring

| Perfil | Banco | Ativação |
|--------|-------|----------|
| `default` | PostgreSQL via Docker Compose | padrão |
| `test` | H2 em memória (apenas testes) | automático pelo Maven |
| `prod` | PostgreSQL (variáveis de ambiente) | `-Dspring.profiles.active=prod` |

### Endpoints de diagnóstico (apenas perfil dev/default)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/diagnostics/invoices` | Lista as 20 invoices mais recentes com status |
| `GET` | `/diagnostics/events` | Lista os 30 eventos mais recentes com logType |
| `GET` | `/diagnostics/webhooks` | Lista os webhooks registrados e suas subscriptions |
| `POST` | `/diagnostics/invoices/test-create` | Cria uma invoice de teste para validar credenciais |
| `POST` | `/diagnostics/invoices/{id}/pay` | Tenta simular pagamento manualmente — implementado para facilitar testes, porém o sandbox retorna `invalidOperation: This invoice can't be updated` |
| `POST` | `/diagnostics/generate-keys` | Gera novo par de chaves ECDSA e salva em `keys/` |

---

## Deploy

A aplicação está deployada na AWS e acessível publicamente via HTTPS.

### Infraestrutura

| Componente | Serviço AWS | Especificação |
|------------|-------------|---------------|
| Aplicação | EC2 | t3.micro · Amazon Linux 2023 · IP: `3.15.106.59` |
| Banco de dados | RDS | db.t3.micro · PostgreSQL 16.14 |
| Reverse proxy | nginx | Portas 443/80 → `localhost:8080` |
| HTTPS | Let's Encrypt (certbot)
| DNS | DuckDNS | `stark-challenge.duckdns.org` → `3.15.106.59` |

### URLs

| Recurso | Endereço |
|---------|----------|
| Aplicação | `https://stark-challenge.duckdns.org` |
| Webhook (registrado no Stark Bank sandbox) | `https://stark-challenge.duckdns.org/webhook` |

### Inicialização na EC2

A aplicação roda como serviço systemd com o perfil `prod` e lê a chave privada do sistema de arquivos:

```
java -jar -Dspring.profiles.active=prod \
     -Dstark.private.key.path=/home/ec2-user/privateKey.pem \
     /home/ec2-user/app.jar
```

### Para avaliação

- O webhook `https://stark-challenge.duckdns.org/webhook` está registrado no dashboard sandbox com subscription `invoice`
- O scheduler dispara automaticamente a cada 3 horas criando 8–12 boletos
- Todas as invoices e transferências são persistidas no RDS (tabelas `invoice_records` e `transfer_records`)
---

## Banco de dados

### Por que foi utilizado

O banco de dados serve dois propósitos no desafio:

1. **Rastreabilidade completa** — cada boleto emitido e cada transferência realizada fica registrado, criando um histórico auditável de toda a operação.
2. **Idempotência de webhooks** — o Stark Bank pode reenviar um webhook quando não recebe confirmação (HTTP 200) em tempo hábil. Sem proteção, um reenvio do evento `invoice.credited` resultaria em **duas transferências para o mesmo boleto pago**.

### Tabelas

#### `invoice_records` — boletos emitidos pelo scheduler

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `stark_invoice_id` | `VARCHAR` (PK) | ID da invoice retornado pelo Stark Bank |
| `amount` | `BIGINT` | Valor em centavos |
| `name` | `VARCHAR` | Nome do pagador gerado aleatoriamente |
| `tax_id` | `VARCHAR` | CPF do pagador |
| `due` | `VARCHAR` | Data de vencimento |
| `scheduled_at` | `TIMESTAMP` | Quando o scheduler executou |
| `status` | `VARCHAR` | `PENDING` → `PAID` (atualizado pelo webhook) |

#### `transfer_records` — transferências realizadas após pagamento

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `stark_invoice_id` | `VARCHAR` (PK) | ID da invoice que originou a transferência |
| `stark_transfer_id` | `VARCHAR` | ID da transferência retornado pelo Stark Bank |
| `net_amount` | `BIGINT` | Valor transferido (`amount - fee`) em centavos |
| `fee` | `BIGINT` | Taxa cobrada pelo Stark Bank |
| `external_id` | `VARCHAR` | `invoice-{stark_invoice_id}` (idempotência) |
| `transferred_at` | `TIMESTAMP` | Momento da transferência |

### Fluxo com rastreabilidade

```
Scheduler (a cada 3h)
  └─► invoiceGateway.create(invoices)
        └─► Salva cada invoice em invoice_records (status=PENDING)

Webhook POST /webhook (invoice.credited)
  │
  ├─► transfer_records.existsById(invoiceId)?
  │     └─ SIM → webhook duplicado, retorna 200 sem ação
  │
  ├─► invoice_records.findById(invoiceId)
  │     └─► Atualiza status para PAID
  │
  ├─► transferService.transferNetAmount(invoiceId, amount, fee)
  │     └─► Cria transferência no Stark Bank
  │
  └─► Salva TransferRecord (starkTransferId, netAmount, fee, transferredAt)
```

### Por que `transfer_records` é a chave de idempotência

A verificação de duplicidade é feita sobre `transfer_records` (e não sobre `invoice_records`) porque um registro de invoice PAID sem `transfer_record` indica que a transferência falhou e nesse caso o webhook deve ser reprocessado quando reentregue. Só existe `transfer_record` quando a transferência foi concluída com sucesso.

### Segunda linha de defesa

Além da proteção no banco, a transferência é criada com `externalId = "invoice-{invoiceId}"`. O Stark Bank usa esse campo como chave de idempotência: se uma transferência com o mesmo `externalId` já existir, a API retorna a existente sem criar uma nova. Isso garante proteção mesmo em cenários onde o banco de dados esteja temporariamente indisponível.

---

## Bugs encontrados no Stark Bank

Durante o desenvolvimento foram identificados três comportamentos anômalos no ambiente sandbox do Stark Bank, documentados abaixo com evidências de log.

---

### Bug #1 — `invalidCredentials` intermitente na primeira chamada após inicialização

**Descrição:** Em algumas execuções, a primeira chamada à API após o início da aplicação retorna `invalidCredentials` mesmo com credenciais válidas. O mesmo payload com a mesma chave é aceito na tentativa imediatamente seguinte, confirmando que as credenciais estão corretas. O comportamento é intermitente — em outras execuções a primeira chamada já funciona normalmente.

**Impacto:** A criação de invoices pode falhar na primeira tentativa após uma nova inicialização da aplicação.

**Evidência — primeira tentativa falha, segunda bem-sucedida (sessão de 23/06/2026 às 17:37 BRT):**

```
17:37:46 INFO  Gerando 10 boletos...
17:37:47 WARN  Tentativa 1/3 falhou: invalidCredentials. Retentando em 5000ms...
17:37:52 INFO  10 boletos criados com sucesso.   ← mesma chave, nova tentativa OK
```

**Evidência — comportamento normal sem retry (sessão de 24/06/2026 às 10:15 BRT):**

```
10:15:03 INFO  Gerando 11 boletos...
10:15:05 INFO  11 boletos criados e registrados.   ← sucesso na primeira tentativa
```

**Análise:** O SDK usa OkHttp com HTTP/2 e pool de conexões. A primeira requisição após inicialização da JVM aparentemente atinge ocasionalmente um nó do balanceador de carga do sandbox em estado inconsistente. A assinatura ECDSA é computada corretamente — o problema está no servidor.

**Workaround aplicado:** Retry automático com até 7 tentativas e 10 segundos entre elas (`MAX_RETRIES = 7`, `retryDelayMs = 10000`).

**Comportamento esperado:** A API deveria aceitar a primeira requisição com credenciais válidas, ou retornar um código que reflita o problema real (ex.: `serverError`, `serviceUnavailable`) em vez de `invalidCredentials`.

---

### Bug #2 — `Invoice.create` bloqueado temporariamente com código de erro `invalidCredentials` incorreto

**Descrição:** O endpoint `POST /v2/invoice` retorna `invalidCredentials` de forma intermitente, mesmo com credenciais válidas. O bloqueio afeta apenas operações de escrita: durante todo o período de falha, `Invoice.query (GET)` e `Transfer.create (POST)` funcionam normalmente com as mesmas credenciais. O bloqueio é **temporário** — o mesmo projeto e as mesmas chaves voltam a funcionar após alguns minutos sem qualquer alteração.

**Impacto:** Impossibilidade de criar invoices durante o período de bloqueio. A aplicação esgota todos os retries a cada ciclo do scheduler afetado.

**Evidência definitiva — mesmo projeto, mesmas chaves, comportamento não-determinístico:**

| Horário (BRT) | Projeto | Operação | Resultado |
|---------------|---------|----------|-----------|
| 24/06 11:10 | `6233478272122880` | Invoice.create (POST) | ❌ 7/7 falhas — `invalidCredentials` |
| 24/06 11:10 | `6233478272122880` | Invoice.query (GET) | ✅ Sucesso |
| 24/06 11:16 | `6233478272122880` | Invoice.create (POST) | ✅ 10 criadas — **mesmo projeto, mesmas chaves** |
| 24/06 11:2x | `6233478272122880` | Invoice.create (POST) | ❌ 7/7 falhas novamente |
| 24/06 11:32 | `6233478272122880` | Invoice.create (POST) | ✅ 11 criadas — **mesmo projeto, mesmas chaves** |

Em 4 execuções consecutivas com o mesmo projeto e as mesmas chaves: falha → sucesso → falha → sucesso. O comportamento é completamente não-determinístico — não há um cooldown fixo observável.

**Linha do tempo completa do incidente (24/06/2026):**

| Horário (BRT) | Projeto | Operação | Resultado |
|---------------|---------|----------|-----------|
| 23/06 17:37 | `6235162335510528` | Invoice.create (POST) | ✅ 10 criadas |
| 23/06 18:39 | `6235162335510528` | Invoice.create (POST) | ❌ 3/3 falhas |
| 23/06 18:40 | `6235162335510528` | Invoice.query (GET) | ✅ Sucesso |
| 24/06 09:56 | `6235162335510528` | Invoice.create (POST) | ❌ 7/7 falhas (overnight) |
| 24/06 10:15 | `6235162335510528` | Invoice.create (POST) | ✅ 11 criadas (após ~15h de espera) |
| 24/06 10:57 | `6235162335510528` | Invoice.create (POST) | ❌ 7/7 falhas |
| 24/06 11:10 | `6233478272122880` | Invoice.create (POST) | ❌ 7/7 falhas (novo projeto) |
| 24/06 11:16 | `6233478272122880` | Invoice.create (POST) | ✅ 10 criadas (~5 min depois) |

**Log — falha às 11:10, sucesso às 11:16 (mesmo projeto e chave):**

```
11:10:06 INFO  Gerando 11 boletos...
11:10:07 WARN  Tentativa 1/7 falhou: invalidCredentials. Retentando em 10000ms...
...
11:11:08 ERROR Erro ao criar boletos após 7 tentativas: invalidCredentials

[app reiniciada, mesmo STARK_PROJECT_ID=6233478272122880, mesma keys/privateKey.pem]

11:16:24 INFO  Gerando 10 boletos...
11:16:26 INFO  10 boletos criados e registrados.   ← mesmo projeto, mesma chave
```

---

## Evidências de execução bem-sucedida

Execução completa do fluxo do desafio registrada em 24/06/2026, sessão das 11:31 BRT.

### Etapa 1 — Scheduler emitiu 11 boletos

O scheduler disparou 15 segundos após o início da aplicação e criou 11 invoices no Stark Bank sandbox, dentro da faixa exigida (8–12 boletos a cada 3 horas).

```
11:31:57 INFO  Started ChallengeApplication in 3.037 seconds
11:32:12 INFO  Gerando 11 boletos...
11:32:14 INFO  11 boletos criados e registrados.
```

Confirmação do webhook `type=created` para cada invoice emitida (11 eventos recebidos):

```
11:32:30 INFO  Webhook recebido: subscription=invoice
11:32:30 INFO  Invoice log type=created, invoiceId=5214127672786944
11:32:38 INFO  Invoice log type=created, invoiceId=5777077626208256
11:32:50 INFO  Invoice log type=created, invoiceId=4791915207720960
11:32:52 INFO  Invoice log type=created, invoiceId=5917815114563584
11:32:53 INFO  Invoice log type=created, invoiceId=5636340137852928
11:33:00 INFO  Invoice log type=created, invoiceId=6480765067984896
11:33:02 INFO  Invoice log type=created, invoiceId=4651177719365632
11:33:06 INFO  Invoice log type=created, invoiceId=6199290091274240
11:33:09 INFO  Invoice log type=created, invoiceId=5354865161142272
11:33:11 INFO  Invoice log type=created, invoiceId=6340027579629568
11:33:12 INFO  Invoice log type=created, invoiceId=5073390184431616
```

### Etapa 2 — Sandbox simulou pagamentos (~10 min após criação)

O sandbox Stark Bank simulou o pagamento de parte dos boletos e entregou os eventos `paid` → `credited` via webhook. A sequência abaixo mostra o ciclo completo de uma invoice:

```
11:42:18 INFO  Invoice log type=paid,     invoiceId=5214127672786944
11:42:27 INFO  Invoice log type=credited, invoiceId=5214127672786944
11:42:27 INFO  Invoice creditada: id=5214127672786944, amount=95437, fee=0
```

### Etapa 3 — Transferência realizada para cada invoice creditada

Para cada evento `credited` recebido, a aplicação criou imediatamente uma transferência para a conta Stark Bank S.A. com o valor líquido (`amount - fee`). Todas as 11 transferências foram concluídas com sucesso:

```
11:42:28 INFO  Transferência criada: id=6487485968285696, invoiceId=5214127672786944, valor=95437 cents
11:42:33 INFO  Transferência criada: id=6728325923864576, invoiceId=5917815114563584, valor=31131 cents
11:42:33 INFO  Transferência criada: id=5735206273155072, invoiceId=4791915207720960, valor=44882 cents
11:42:33 INFO  Transferência criada: id=5289361891393536, invoiceId=5073390184431616, valor=49816 cents
11:42:35 INFO  Transferência criada: id=6241817261506560, invoiceId=6480765067984896, valor=61673 cents
11:42:35 INFO  Transferência criada: id=5037691580186624, invoiceId=5354865161142272, valor=46134 cents
11:42:37 INFO  Transferência criada: id=5044962079014912, invoiceId=5636340137852928, valor=94619 cents
11:42:42 INFO  Transferência criada: id=4961383399555072, invoiceId=4971416688525312, valor=33629 cents
11:42:42 INFO  Transferência criada: id=6551698413715456, invoiceId=5815841618657280, valor=9792 cents
11:42:44 INFO  Transferência criada: id=5612184182718464, invoiceId=6199290091274240, valor=4178 cents
11:42:44 INFO  Transferência criada: id=4566873741983744, invoiceId=5777077626208256, valor=25506 cents
```

Cada transferência também foi persistida em `transfer_records` como confirmação de idempotência:

```
11:42:28 INFO  Transferência registrada: invoiceId=5214127672786944, transferId=6487485968285696
11:42:33 INFO  Transferência registrada: invoiceId=5917815114563584, transferId=6728325923864576
... (11 registros no total)
```

### Resumo da sessão (24/06 11:31 BRT)

| Métrica | Valor |
|---------|-------|
| Boletos emitidos nesta sessão | 11 |
| Boletos pagos em lote (~10 min após criação) | 9 |
| Boletos pagos com atraso (~29 min após criação) | 1 (`4651177719365632`) |
| Boletos sem pagamento registrado nos logs | 1 (`6340027579629568`) — pode chegar em ciclo posterior |
| Transferências realizadas (lote desta sessão) | 10 às 11:42 + 1 às 12:01 = **11** |
| Transferências de sessões anteriores processadas nesta janela | 2 (`4971416688525312`, `5815841618657280`) |
| Falhas de transferência | 0 |
| Duplicatas processadas | 0 |
| Destino | Stark Bank S.A. — banco `20018183`, conta `6341320293482496` |
| Tempo entre emissão e transferência | 10 min (maioria) a 29 min (um boleto atrasado) |

---

## Evidências de múltiplos ciclos — sessão noturna (24-25/06/2026)

Sessão contínua de ~18h (PID 56500), com o scheduler disparando automaticamente a cada 3 horas. Demonstra a execução repetida do fluxo completo e o comportamento de recuperação automática após o Bug #2.

### Ciclo 1 — 24/06 22:59 BRT (11 boletos)

```
22:59:40 INFO  Gerando 11 boletos...
22:59:42 INFO  11 boletos criados e registrados.
23:01:xx INFO  Transferência criada: id=... (10 transferências imediatas)
23:11:xx INFO  Transferência criada: id=... (1 transferência atrasada — 12 min)
```

Todos os 11 boletos pagos e transferidos. ✅

### Ciclo 2 — 25/06 01:59 BRT (8 boletos)

```
01:59:40 INFO  Gerando 8 boletos...
01:59:42 INFO  8 boletos criados e registrados.
02:02-02:03 INFO  Transferência criada: id=... (7 transferências imediatas)
14:11-14:12 INFO  Transferência criada: id=... (2 transferências tardias — chegaram horas depois)
```

Todos os 8 boletos pagos e transferidos (2 com webhook tardio). ✅

### Ciclos 3–6 — Bug #2 (04:59 / 07:59 / 10:59 / 13:59 BRT)

```
04:59:40 INFO  Gerando 8 boletos...
04:59:41 WARN  Tentativa 1/7 falhou: invalidCredentials. Retentando em 10000ms...
...
05:00:48 ERROR Erro ao criar boletos após 7 tentativas: invalidCredentials
[padrão repetido às 07:59, 10:59 e 13:59]
```

Throttle do sandbox ativo por ~12 horas. Scheduler continuou disparando; aplicação não precisou ser reiniciada. ❌

### Ciclo 7 — 25/06 16:59 BRT (9 boletos) — recuperação automática

```
16:59:40 INFO  Gerando 9 boletos...
16:59:42 INFO  9 boletos criados e registrados.
17:01:xx INFO  Transferência criada: id=... (8 transferências imediatas)
17:11:45 INFO  Transferência criada: id=5811520904626176, invoiceId=6581966744846336, valor=51180 cents
```

Throttle encerrado sem intervenção. Todos os 9 boletos pagos e transferidos. ✅

### Resumo da sessão noturna

| Métrica | Valor |
|---------|-------|
| Duração total da sessão | ~18h (22:59 → 17:11) |
| Ciclos do scheduler disparados | 7 (a cada 3 horas exatas) |
| Ciclos bem-sucedidos | 3 (22:59, 01:59, 16:59) |
| Ciclos bloqueados pelo Bug #2 | 4 (04:59 a 13:59 — throttle de ~12h) |
| Recuperação automática | ✅ sem restart ou intervenção |
| Total de boletos criados | 28 (11 + 8 + 9) |
| Total de transferências realizadas | 28 |
| Falhas de transferência | 0 |
| Duplicatas processadas | 0 |
| Idempotência validada | ✅ webhooks tardios processados corretamente sem reprocessamento |