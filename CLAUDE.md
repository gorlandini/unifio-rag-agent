# CLAUDE.md — unifio-rag-agent

> Arquivo de contexto do projeto. O Claude Code lê este arquivo automaticamente no início de cada sessão.
> **Regra para o Claude:** ao terminar qualquer tarefa, atualize as seções "Progresso" e "Log de sessões" abaixo antes de encerrar.

## O que é o projeto
Assistente RAG institucional para a UNIFIO: responde perguntas sobre documentos da universidade (PPC, regulamentos etc.) citando documento e página.
Estou construindo **incrementalmente**, seguindo um tutorial por marcos (Marco 0 → 7). Cada marco precisa rodar sozinho antes de avançar.
**Não adiantar infraestrutura** (Docker, Postgres, Kafka) antes do marco correspondente.

## Stack
- Java 21, Spring Boot, Maven (`mvnw`)
- LangChain4j (versão via `langchain4j-bom`, propriedade `${langchain4j.version}`)
- Ollama local (`http://localhost:11434`)
  - Chat: `qwen3:8b` (configurável em `unifio.chat.model`; antes `llama3.1`)
  - Embeddings: `bge-m3` (1024 dimensões; configurável em `unifio.embedding.model`; antes `nomic-embed-text`, 768)
- Futuro: PDFBox (Marco 4), Postgres + pgvector via Docker (Marco 5), Kafka (Marco 7), Spring Security e front Angular já existem em outro tutorial e plugam nos endpoints REST.

## Ambiente
- Windows + IntelliJ IDEA
- Pacote base: `com.br.unifioragagent`
- Estrutura atual:
  - `config/LangChainConfig` — beans `ChatModel`, `EmbeddingModel`, `EmbeddingStore<TextSegment>` (`PgVectorEmbeddingStore`, dim em `unifio.embedding.dimension`)
  - `controller/ChatController` — `POST /chat` (sem contexto) e `POST /chat2` (RAG top-4 + expansão de vizinhos ±1 página, máx. 6)
  - `controller/DocumentController` — `POST /institutional-documents` (multipart `files`; salva em `uploads/` e publica no Kafka, 202 `enfileirado`) e `DELETE /institutional-documents/{fileName}`
  - `messaging/` — `IngestionProducer` (tópico `document-ingestion`, payload `caminho|nome`), `IngestionConsumer` (`@KafkaListener`, grupo `rag-ingestion`, chama `ingestPdf`), `IngestionEvent`
  - `service/DocumentIngestionService` — PDFBox por página → split 500/50 → embeddings (metadados `fileName`, `page`, `uploaded_at`); `deleteByFileName` via `removeAll(filtro)`
  - `eval/` — `gabarito.json` (perguntas + páginas esperadas) e amostras do Docling
  - `EmbeddingDemoRunner` — CommandLineRunner do Marco 2 (demonstração, pode ser removido)
  - `UnifioRagAgentApplication`

## Convenções
- Código conciso, **sempre com imports completos**.
- Injeção por construtor; DTOs como `record`.
- Mensagens, prompts e dados de exemplo em português.
- Explicações práticas e diretas; nada de refatorar além do marco atual sem eu pedir.

## Roteiro (tutorial)
| Marco | Entrega | Endpoint / artefato |
|---|---|---|
| 0 | Ollama rodando + modelos baixados | — |
| 1 | API chama o modelo | `POST /chat` (sem contexto) |
| 2 | Entender embeddings (similaridade de cosseno) | `EmbeddingDemoRunner` |
| 3 | RAG em memória com fatos fixos | `InMemoryEmbeddingStore` + `/chat` com busca top-2 |
| 4 | Ingestão de PDF real (síncrona) | `POST /institutional-documents` (PDFBox, split 500/50, metadados `fileName`, `page`) |
| 5 | Persistência | `PgVectorEmbeddingStore` (tabela `institutional_embeddings`, dim **1024** por causa do `bge-m3`; o tutorial diz 768) + docker-compose |
| 6 | Citação de fonte | `ChatResponse(answer, sources[fileName, page])`, top-4 |
| 7 | Ingestão assíncrona | Kafka: upload publica evento, `@KafkaListener` faz o ingest |

## Progresso
- [x] Marco 0 — Ollama instalado, `llama3.1` e `nomic-embed-text` funcionando
- [x] Marco 1 — `/chat` respondendo
- [x] Marco 2 — demo de embeddings rodou (dim 768; sim. relacionada ≈ 0.708, não relacionada ≈ 0.601)
- [x] Marco 3 — RAG em memória (`/chat2`); evoluiu para top-4 + expansão de vizinhos
- [x] Marco 4 — ingestão de PDF (PPC 192 págs + horário) funcionando; limites conhecidos com tabelas/listas (ver log)
- [ ] Marco 5 — **em andamento**: `docker-compose.yml` (pgvector/pg16, `ragdb`, `rag`/`rag`, 5432) + `PgVectorEmbeddingStore` (tabela `institutional_embeddings`, `vector(1024)`) funcionando; reenvio sem duplicar + `DELETE` por arquivo testados; falta reenviar os PDFs reais e rodar o gabarito; depois extras: busca híbrida (hoje `/chat2` é só vetorial) e `docling-serve`
- [ ] Marco 6
- [x] Marco 7 — ingestão assíncrona via Kafka (KRaft, `apache/kafka:3.8.0`) testada; ver log

## Decisões e armadilhas já descobertas
- **BOM não adiciona dependência**: `langchain4j-bom` em `<dependencyManagement>` só fixa versões. Cada módulo precisa estar em `<dependencies>` (sem `<version>`).
- `InMemoryEmbeddingStore` (`dev.langchain4j.store.embedding.inmemory`) exigiu declarar o artefato `dev.langchain4j:langchain4j`.
- **Similaridade de cosseno**: os valores absolutos do tutorial (0.71 vs 0.18) eram ilustrativos. Com `nomic-embed-text` em PT, frases não relacionadas dão ~0.6. O que importa é o **ranking**. Não usar threshold fixo copiado do tutorial; preferir top-K e calibrar `minScore` com perguntas reais.
- `nomic-embed-text` rende melhor com prefixos `search_query: ` (pergunta) e `search_document: ` (trechos indexados). Pendente decidir se adoto.
- Alternativa multilíngue a avaliar: `bge-m3` (1024 dim, exige mudar `dimension` do pgvector no Marco 5).
- **Mesmo `EmbeddingModel` para indexar e para consultar.** Trocar o modelo exige reindexar tudo.
- API do LangChain4j muda entre versões (ex.: `ChatLanguageModel`/`generate()` → `ChatModel`/`chat()` na linha 1.x). Conferir a versão do pom antes de sugerir código do tutorial.

## Log de sessões
<!-- Claude: adicione uma linha por sessão, mais recente no topo. Formato: AAAA-MM-DD — o que foi feito — próximo passo -->
- 2026-09-28 — Marco 7 (código do usuário: producer/consumer/`DocumentController` + `spring-boot-starter-kafka`). Corrigido: nome da classe ≠ arquivo (renomeado para `DocumentController.java`), `DELETE` que tinha sumido restaurado, `spring.` faltando no `auto-offset-reset` (sem ele vale `latest` e o 1º upload pode se perder), `uploads/` no `.gitignore`. Testado na 8081 com PDF sintético: POST → 202 → consumer ingere em ~9 s (2 trechos) → reenvio continua 2 → DELETE zera; PPC/Horário intactos. Armadilhas: após renomear classe, `mvnw clean` (o `.class` antigo em `target/` causou "Ambiguous mapping"); "not the correct coordinator" no 1º start é INFO normal. Pendências: DELETE não apaga o PDF de `uploads/`; erro no consumer só vai para log (sem DLT). Janela entre upload e fim da ingestão **não é problema** (upload e perguntas acontecem em momentos distintos na universidade); um aviso no `/chat2` chegou a ser implementado e foi removido a pedido. — próximo: commit do Marco 7 e voltar ao gabarito.
- 2026-09-28 — Adiantado a pedido: serviço `kafka` (`apache/kafka:3.8.0`, KRaft nó único, 9092) no `docker-compose.yml` e `spring.kafka.bootstrap-servers=localhost:9092` no `application.properties` (sem efeito até entrar `spring-kafka` no pom). Container não subido. — próximo: rodar o gabarito com o PPC no pgvector; Kafka de fato no Marco 7.
- 2026-09-25 — Marco 5: `docker-compose.yml` (pgvector/pg16) e `LangChainConfig` com `PgVectorEmbeddingStore` (localhost:5432/ragdb, `createTable(true)`, dim em `unifio.embedding.dimension=1024`); removidos os fatos de exemplo do Marco 3. App subiu na 8081 e criou `institutional_embeddings` (`embedding_id uuid`, `embedding vector(1024)`, `text`, `metadata json`; sem índice vetorial, ok para ~1k linhas). Confirmado que `/chat2` é só vetorial. Armadilhas: reenviar o mesmo PDF **duplica** trechos; trocar modelo de embedding exige `DROP TABLE`. Depois: aplicado o útil de um handoff externo — reenvio do mesmo PDF agora apaga a versão anterior (`removeAll` por `fileName`, só depois de o PDF novo abrir), metadado `uploaded_at` e `DELETE /institutional-documents/{fileName}` (204). Testado na 8081 com PDF sintético de 2 págs: 2 trechos → reenvio continua 2 → delete zera. Descartado do handoff: `ApachePdfBoxDocumentParser` (perde a página), campo `source` (redundante com `fileName`), fontes no `/chat` (é o Marco 6). Obs.: Ollama estava fora no início do teste (ConnectException) — conferir antes de subir a app. — próximo: reenviar PPC + horário, rodar o gabarito e depois busca híbrida (`tsvector` `portuguese` + GIN + RRF).
- 2026-09-24 — Teste da expansão de vizinhos (instância na porta 8081, qwen3:8b + bge-m3): mecânica funciona (±1 página, máx. 6, sem duplicar), mas **sem ganho no gabarito** — busca não chega perto da matriz (top-4 da pergunta de disciplinas: p.47, 29, 68, 27) nem da p.3. "6º semestre?" e coordenador → "não está no contexto"; 1970 ✅. Ponto positivo: qwen3 diz "não sei" em vez de inventar. ~25 s por resposta (maior parte é o raciocínio do qwen3). Expansão só deve render quando a busca melhorar (híbrida no Marco 5).
- 2026-09-24 — Docling no ementário (seção 3.4.1, págs. 69–72): detectou como título "3.4.1. Disciplinas", "PRIMEIRO SEMESTRE", cada nome de disciplina, "EMENTA" e "BIBLIOGRAFIA BÁSICA/COMPLEMENTAR", mas **todos no mesmo nível** (`##` no MD, `level=1` no JSON), sem hierarquia. Dividir só "por título" separaria EMENTA/BIBLIOGRAFIA do nome da disciplina. Próximo: inferir hierarquia de forma genérica (numeração 3.4.1 → profundidade; títulos repetidos como EMENTA = rótulos internos; ou LLM classificando a lista de títulos).
- 2026-09-24 — Chat trocado para `qwen3:8b` (propriedade `unifio.chat.model`). Ollama 0.34 devolve o raciocínio do qwen3 no campo `thinking` separado, então o `content` lido pelo LangChain4j vem limpo. Teste isolado (seção certa no contexto): coordenador ✅ com o prompt atual; disciplinas "não é possível determinar" com o prompt atual; com prompt "você pode listar, contar e resumir", acertou o total 46 mas errou a divisão por semestre e passou a recusar o coordenador (cauteloso demais). Contagem por LLM segue não confiável. Prompt do `/chat2` **não** foi alterado.
- 2026-09-24 — Teste A/B (script Python fora da app, bge-m3 + llama3.1, top-4): Docling sozinho **não mudou as respostas**, porque as seções dele não entram no top-4 (matriz: pos. 27 de 1024; pág. 3 do coordenador: pos. 129). Indexar resumo gerado por LLM melhorou pouco (matriz → pos. 16). Com a seção certa forçada no contexto: llama3.1 listou 39 de 46 disciplinas (pulou o 1º semestre) e respondeu "Não sei" para o coordenador mesmo com o nome no texto. Conclusão: gargalos são (1) busca vetorial para tabelas/listas → busca híbrida/reranker com K maior; (2) llama3.1 8B na geração → testar modelo melhor.
- 2026-09-24 — Teste do Docling (CLI, venv no scratchpad, `--no-ocr`) nas págs. 3 e 52–55: matriz saiu como tabela Markdown limpa (46 disciplinas + 5 optativas corretas; falhas menores: tabela quebra por página, coluna "Período" às vezes vazia). Amostra em `eval/ppc-trecho-docling.md`. Armadilhas: Python precisa de `truststore` (certificado autoassinado na cadeia HTTPS da rede); torch instalado é CPU (GPU RTX 4060 8 GB não usada). Integração planejada via `docling-serve` no Marco 5.
- 2026-09-24 — `/chat2` com expansão de vizinhos (top-4 → páginas ±1, máx. 6 págs, trechos buscados por filtro `fileName`+`page` e reordenados por `index`); `numCtx(8192)` no chat. Compila; **não testado** (porta 8080 ocupada pela instância do usuário). Criado `eval/gabarito.json` (3 perguntas: fato em parágrafo p.9/11, fato em lista p.3, enumeração da matriz p.52–55 = 46 componentes). Matriz curricular = seção 3.3.1, págs. 52–55.
- 2026-09-23 — Trocado para `bge-m3`. Demo Marco 2: relacionada 0.749 vs não relacionada 0.462 (gap 0.29, era 0.1 com nomic). Ingestão PPC (192 págs) + Horário (3 págs) em ~48s. "Primeiro curso superior de Ourinhos?" → acertou (p.9 e p.11 no top-2). "Quem é o coordenador?" → p.3 segue fora do top-6 (scores 0.80–0.815 todos em trechos sobre coordenação sem o nome): problema estrutural (nome em lista de cargos), não só do modelo. Obs.: `mvnw spring-boot:run` falha no testCompile porque o pom não tem `spring-boot-starter-test` (usar `-Dmaven.test.skip=true` ou adicionar a dependência).
- 2026-09-23 — Teste com PPC real: "Quem é o coordenador?" → "Não sei" (nome está numa lista de cargos na p.3; "coordenador" aparece 43x no PPC e outros trechos ganham no ranking). `/chat2` passou para `maxResults(6)` com log de debug (score | fileName p.page | texto). Modelo de embedding agora configurável em `unifio.embedding.model` (application.properties) — próximo: montar gabarito de perguntas (pergunta → página esperada) e comparar recall@K entre `nomic-embed-text` e `bge-m3`, e entre tamanhos de chunk.
- 2026-09-23 — Commit `4331017` enviado ao GitHub (`origin/main`): Marcos 1–3, incluindo `POST /chat2` com busca top-2 e prompt com contexto.
- 2026-09-22 — Marcos 0–2 concluídos; iniciado Marco 3 (dependência `langchain4j` adicionada para `InMemoryEmbeddingStore`) — próximo: ChatController com busca top-2 e montagem do prompt.
