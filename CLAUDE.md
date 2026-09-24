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
  - Chat: `llama3.1`
  - Embeddings: `nomic-embed-text` (768 dimensões)
- Futuro: PDFBox (Marco 4), Postgres + pgvector via Docker (Marco 5), Kafka (Marco 7), Spring Security e front Angular já existem em outro tutorial e plugam nos endpoints REST.

## Ambiente
- Windows + IntelliJ IDEA
- Pacote base: `com.br.unifioragagent`
- Estrutura atual:
  - `config/LangChainConfig` — beans `ChatLanguageModel`/`ChatModel`, `EmbeddingModel`, `EmbeddingStore<TextSegment>`
  - `controller/ChatController` — `POST /chat`
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
| 5 | Persistência | `PgVectorEmbeddingStore` (tabela `institutional_embeddings`, dim 768) + docker-compose |
| 6 | Citação de fonte | `ChatResponse(answer, sources[fileName, page])`, top-4 |
| 7 | Ingestão assíncrona | Kafka: upload publica evento, `@KafkaListener` faz o ingest |

## Progresso
- [x] Marco 0 — Ollama instalado, `llama3.1` e `nomic-embed-text` funcionando
- [x] Marco 1 — `/chat` respondendo
- [x] Marco 2 — demo de embeddings rodou (dim 768; sim. relacionada ≈ 0.708, não relacionada ≈ 0.601)
- [ ] Marco 3 — **em andamento**: bean `embeddingStore` com `InMemoryEmbeddingStore` sendo criado
- [ ] Marco 4
- [ ] Marco 5
- [ ] Marco 6
- [ ] Marco 7

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
- 2026-09-22 — Marcos 0–2 concluídos; iniciado Marco 3 (dependência `langchain4j` adicionada para `InMemoryEmbeddingStore`) — próximo: ChatController com busca top-2 e montagem do prompt.
