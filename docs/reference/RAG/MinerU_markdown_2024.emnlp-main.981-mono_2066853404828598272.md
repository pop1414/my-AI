# 寻找检索增强生成的最佳实践

王晓华，王正华，高轩，张斐然，吴一昕，徐志博，史天元，王正源，李时政，钱琦，尹瑞成，吕长泽，郑晓庆 $^{*}$ ，黄萱菁 中国上海，复旦大学计算机科学技术学院 上海市智能信息处理重点实验室 {xiaohuawang22}@m.fudan.edu.cn {zhengxq,xjhuang}@fudan.edu.cn

# 摘要

检索增强生成（RAG）技术已被证明能有效整合最新信息、减少幻觉并提升回复质量，尤其在专业领域表现突出。尽管已有许多RAG方法通过查询依赖检索来增强大语言模型，但这些方法仍存在实现复杂、响应时间长等问题。通常，一个RAG工作流包含多个处理步骤，每个步骤都有多种执行方式。我们在此研究现有RAG方法及其潜在组合，以识别最优RAG实践。通过大量实验，我们提出了几种兼顾性能与效率的RAG部署策略。此外，我们证明多模态检索技术能显著提升视觉输入相关的问答能力，并通过"检索即生成"策略加速多模态内容生成。相关代码与资源见https://github.com/FudanDNN-NLP/RAG。

许多RAG方法被提出，通过查询相关的检索来增强大型语言模型（LLMs）（Cai等人，2022；Gao等人，2023；Li等人，2022）。一个典型的RAG工作流程通常包含多个中间处理步骤：查询分类（确定给定输入查询是否需要检索）、检索（高效获取与查询相关的文档）、重排序（根据与查询的相关性优化检索文档的顺序）、重新打包（将检索到的文档组织成结构化的形式以便更好地生成）、摘要（从重新打包的文档中提取用于响应生成的关键信息并消除冗余）等模块。实施RAG还需要决定如何适当将文档分割成块、用于语义表示这些块的嵌入类型、高效存储特征表示的向量数据库的选择，以及有效微调LLMs的方法（见图1）。

# 1 引言

生成式大语言模型虽通过强化学习(Ouyang et al., 2022)或轻量级替代方案(Liu et al., 2023; Rafa ilov et al., 2023; Yuan et al., 2023; Zhao et al., 2023b)与人类偏好对齐，但仍易产生过时信息或编造事实。检索增强生成(RAG)技术通过融合预训练与基于检索的模型优势解决这些问题，为提升模型性能提供了稳健框架(Gao et al., 2023)。此外，只要提供查询相关文档，RAG即可在无需更新模型参数的情况下，为特定组织和领域快速部署应用程序。

增加复杂性和挑战的是每个处理步骤实施方式的可变性。例如，在检索与输入查询相关的文档时，可以采用多种方法。一种方法是先重写查询，再利用重写后的查询进行检索（Ma et al., 2023a）。另一种方法是先生成查询的伪响应，然后比较这些伪响应与后端文档的相似度来检索（Gao et al., 2022）。还有一种是直接使用嵌入模型，这类模型通常利用正负查询-响应对进行对比训练（Wang et al., 2022; Xiao et al., 2023）。每个步骤所选用的技术及其组合方式，都会显著影响RAG系统的效果和效率。据我们所知，目前尚未有系统性工作致力于追求RAG的最佳实现，尤其是针对整个RAG工作流程。

在本研究中，我们旨在通过大量实验确定RAG的最佳实践。鉴于测试所有可能方法组合的不可行性，我们采用三步法来识别最优RAG实践。首先，针对每个RAG步骤（或模块）比较代表性方法，筛选出最多三种效果最优的方法。接着，通过逐个测试各步骤的单一方法（同时保持其他RAG模块不变），评估每种方法对整体RAG性能的影响。此举能让我们根据其在响应生成过程中的贡献及与其他模块的交互作用，确定各步骤最有效的方法。当某个模块选定最佳方法后，该方法将被用于后续实验。最后，我们针对效率可能优先于性能（或相反情况）的不同应用场景，实证探索了几种有前景的组合方案。基于这些发现，我们提出了若干兼顾性能与效率的RAG部署策略。

本研究的贡献有三方面：

- 通过广泛的实验，我们深入研究了现有的RAG方法及其组合，以确定并推荐最优的RAG实践。

- 我们引入了一个全面的评估指标框架及相应的数据集，以全面评估检索增强生成模型的性能，涵盖通用、专业（或领域特定）及RAG相关能力。

- 我们证明了多模态检索技术的集成可以大幅提升基于视觉输入的问答能力，并通过“检索即生成”的策略加速多模态内容的生成。

# 2 相关工作

确保大型语言模型（LLMs）如ChatGPT（OpenAI，2023）和LLaMA（Touvron等人，2023a）生成回答的准确性至关重要。然而，简单地增大模型规模并不能从根本上解决幻觉问题（Wang等人，2023b；Zhang等人，2023c），尤其是在知识密集型任务和专业领域中。检索增强生成（RAG）通过从外部检索相关文档{nal知识库，为LLMs提供准确、实时、领域特定的上下文（Gao等，2023年）。}以往的工作通过查询与检索转换、提升检索器性能以及微调检索器和生成器，优化了RAG流程。这些优化改善了输入查询、检索机制和生成过程之间的交互，确保了回答的准确性和相关性。

# 2.1 查询与检索转换

有效的检索需要查询准确、清晰且详细。即使转换成嵌入向量，查询与相关文档之间的语义差异仍可能存在。以往的研究探索了通过查询转换来增强查询信息的方法，从而提升检索性能。例如，Query2Doc (Wang 等人, 2023a) 和 HyDE (Gao 等人, 2022) 从原始查询生成伪文档以增强检索，而 TOC (Kim 等人, 2023) 则将查询分解为子查询，通过聚合检索到的内容来得到最终结果。

其他研究则聚焦于转换检索源文档。LlamaIndex (Liu, 2022) 提供了一个接口，可为检索文档生成伪查询，从而改善与真实查询的匹配。一些工作采用对比学习，使查询与文档嵌入在语义空间中更加接近 (Li 等, 2023; Xiao 等, 2023; Zhang 等, 2023a)。对检索到的文档进行后处理是提升生成器输出的另一种方法，相关技术包括层次化提示摘要 (Jiang 等, 2023a) 以及使用抽象式与抽取式压缩器 (Xu 等, 2023) 来缩短上下文长度并消除冗余 (Wang 等, 2023c)。

# 2.2 检索器增强策略

文档分块和嵌入方法对检索性能有显著影响。常见的分块策略将文档分割成多个块，但确定最佳的块长度可能具有挑战性。过小的块可能会割裂句子，而过大的块可能包含无关上下文。LlamaIndex（Liu, 2022）优化了如 Small2Big 和滑动窗口的分块方法。检索到的块可能不相关，且数量可能很大，因此需要重排序以过滤不相关的文档。一种常见的重排序方法使用深度语言模型，如 BERT（Nogueira et al., 2019）、T5（Nogueira et al.,

![image](https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/68029b57c63c0d5644cefd4c77a3c42a8ab3773e90750484914fd08dd9aab201.jpg)



图1：检索增强生成工作流程。本研究通过大量实验考察了各组件的贡献，并提供了最佳RAG实践的见解。每个组件考虑的可选方法以粗体字体指示，而下划线方法表示各模块的默认选择。蓝色字体标明的方法表示根据经验确定的最佳表现选择。


2020)，或 LLaMA (Ma 等人，2023b)，该方法在重排序时推理步骤较慢，但能获得更好的性能。TILDE (Zhuang 和 Zuccon，2021a,b) 通过预先计算并存储查询词项的可能性来实现高效性，基于其总和来对文档进行排序。

# 2.3 检索器与生成器的微调

在RAG框架内进行微调对于优化检索器和生成器至关重要。一些研究专注于微调生成器以更好地利用检索器提供的上下文（Liu et al., 2024b；Luo et al., 2023；Zhang et al., 2024b），确保生成的内容忠实且稳健。其他研究则微调检索器，使其学会为生成器检索有益的段落（Iza card et al., 2022；Shi et al., 2023；Zhang et al., 2024a）。整体方法将RAG视为一个集成系统，同时微调检索器和生成器以提升整体性能（Gu et al., 2020；Lin et al., 2023；Zamani and Bendersky, 2024），尽管这增加了复杂性和集成挑战。

多项综述已广泛讨论了当前的RAG系统，涵盖文本生成（Cai et al., 2022; Li et al., 2022）、与LLMs的集成（Gao et al., 2023; Huang and Huang, 2024）、多模态（Zhao et al., 2023a）以及AI生成内容（Zhao et al., 2024）等方面。尽管这些综述全面概述了现有的RAG方法，但选择适合实际实施的算法仍然

具有挑战性。在本文中，我们专注于应用RAG方法的最佳实践，推进对LLMs中RAG的理解和应用。

# 3 RAG 工作流

在本节中，我们详细介绍了RAG工作流程的各个组成部分。对于每个模块，我们回顾了常用的方法，并为最终的流水线选择了默认方法和替代方法。第4节将讨论最佳实践。图1展示了每个模块的工作流程和方法。附录A中提供了详细的实验设置，包括数据集、超参数和结果。

# 3.1 查询分类

并非所有查询都需要借助检索增强，因为大语言模型自身具备相应的能力。虽然RAG可以提升信息准确性并减少幻觉，但频繁检索会延长响应时间。因此，我们首先对查询进行分类以确定是否需要进行检索。需要检索的查询通过RAG模块处理；其他查询则由大语言模型直接处理。

当所需知识超出模型参数范围时，通常建议采用检索机制。但具体是否需要检索因任务类型而异。例如，2023年前训练的大语言模型处理"Sora由OpenAI研发"这类翻译请求时无需检索。相反，若收到同一主题的介绍性请求，则需借助检索提供相关信息。

为解决这一问题，我们提出将任务按类型分类，以判断查询是否需要检索。我们根据任务是否提供充分信息将15种任务进行分类，具体任务和示例见图2。对于完全基于用户提供信息的任务，我们标记为“信息充足”，无需检索；反之则标记为“信息不足”，可能需要检索。我们创建了一个包含111K样本的数据集，覆盖15种不同的任务类型，其中64K样本标记为“需要检索”，47K样本标记为“无需检索”。我们训练了一个分类器以实现该决策过程的自动化。具体实验结果见附录A.1。第4节探讨了查询分类对工作流的影响，比较了有分类和无分类的场景。

# 3.2 组块

将文档分块成较小的片段对于提高检索精度和避免大型语言模型中的长度问题至关重要。此过程可以在不同粒度级别上应用，例如标记、句子和语义级别。

- 标记级分块很直接，但可能会分割句子，进而影响检索质量。

- 语义级分块使用大语言模型来确定断点，保留上下文但耗时。

- 句子级分块在保留文本语义与简单性和效率之间取得了平衡。

在本研究中，我们采用句子级分块，在简洁性与语义保留之间取得平衡。我们从四个维度对分块进行考察：

块大小对性能有显著影响。较大的块能提供更多上下文，增强理解但会增加处理时间。较小的块能提高检索召回率并减少时间，但可能缺乏足够的上下文。

分块技术，如从小到大和滑动窗口等先进技术，通过组织语块间的关系来提升检索质量。小型语块用于匹配查询，并返回包含这些小语块及其上下文信息的大语块。

元数据增强 通过元数据（如标题、关键词和假设性问题）增强文本块，可以改善检索效果，提供更多方式对检索到的文本进行后处理，并帮助大语言模型更好地理解检索到的信息。

嵌入模型：选择合适的嵌入模型对于查询与文本块的有效语义匹配至关重要。基于 FlagEmbedding $^{1}$ 的评估模块，我们选择了 LLM-Embedder (Zhang et al., 2023a)，因为它在性能和大小之间取得了平衡。

关于元数据包含的详细研究将在未来的工作中进行。关于分块大小的影响、先进的分块技术以及不同嵌入模型的对比实验的进一步讨论，请参见附录A.2。

# 3.3 向量数据库

向量数据库存储嵌入向量及其元数据，通过各种索引和近似最近邻（ANN）方法，能够高效检索与查询相关的文档。

为研究选取合适的向量数据库，我们依据四项关键标准评估了多个选项：多种索引类型、十亿级向量支持、混合搜索及云原生能力。选定这些标准，是因其对现代云基础设施中的灵活性、可扩展性及部署便捷性具有重要影响。多种索引类型提供了根据不同数据特征与用例优化搜索的灵活性。十亿级向量支持对于处理大语言模型应用中的海量数据集至关重要。混合搜索将向量搜索与传统关键词搜索相结合，提升了检索精度。最后，云原生能力确保了云环境中的无缝集成、可扩展性和管理。表6详细对比了五款开源向量数据库：Weaviate、Fai ss、Chroma、Qdrant与Milvus。

我们的评估显示，Milvus 在评估的数据库中脱颖而出，成为最全面的解决方案，满足了所有关键标准，并超越了其他开源选项。

# 3.4 检索方法

给定一个用户查询，检索模块根据查询与文档之间的相似度，从预先构建的语料库中选择前k篇相关文档。然后生成模型利用这些文档为查询制定合适的回复。然而，原始查询往往因表达不佳和缺乏语义信息（Gao等，2023）而表现不佳，对检索过程产生负面影响。为了解决这些问题，我们评估了三种查询转换

使用第3.2节推荐的LLM-Embedder作为查询和文档编码器的方法：

- 查询重写：查询重写通过优化查询，使其更好地匹配相关文档。受Rewrite-Retrieve-Read框架（Ma et al., 2023a）的启发，我们提示大型语言模型（LLM）重写查询，以提升性能。

- 查询分解：这种方法涉及基于从原始查询中派生的子问题来检索文档，其理解和处理起来更为复杂。

- 伪文档生成：该方法基于用户查询生成假设性文档，并利用假设性答案的嵌入表示来检索相似文档。一个著名的实现是HyDE（Gao et al., 2022），

最近的研究，如Sawarkar等人（2024）所示，表明将基于词汇的搜索与向量搜索相结合可以显著提升性能。在本研究中，我们使用BM25进行稀疏检索，并使用Contriever（Izacard等人，2021），一种无监督对比编码器，进行密集检索，作为基于Thakur等人（2021）的两个稳健基线。

我们评估了不同搜索方法在 TREC DL 2019 和 2020 段落排序数据集上的表现。表 7 所示的结果表明，有监督方法显著优于无监督方法。结合 HyDE 和混合搜索，LLM-Embedder 取得了最高得分。然而，查询重写和查询分解并未同样有效地提升检索性能。综合考虑最佳性能和可容忍的延迟，我们推荐将结合 HyDE 的混合搜索作为默认检索方法。出于效率考量，混合搜索融合了稀疏检索（BM25）与稠密检索（原始嵌入），以相对较低的延迟实现了显著性能。关于 HyDE 以及混合搜索超参数的更多实现细节与实验，请参见附录 A.3。

# 3.5 重排序方法

在初步检索之后，会采用重排序阶段来进一步提升所检索文档的相关性，确保最相关的信息排在前面。通过运用更精确的方法，文档得以更有效地重新排序，从而提升查询与排名靠前文档之间的相似度。

在我们的重排序模块中，我们考虑两种方法：DLM 重排序，该方法利用了 classi-fication，以及 TILDE Reranking，它关注查询似然度。这些方法分别优先考虑性能和效率。

- DLM重排序：利用深度语言模型（DLM）的重排序器（Ma et al., 2023b; Nogueira et al., 2020, 2019）是一种代表性方法，虽效率有所降低，但通常能提供最佳性能。模型经微调后，根据用户查询与候选文档的相关性，预测目标标记为“真”或“假”。模型以查询和文档拼接作为输入进行微调，并相应标记。推理时，对于每个查询，文档则按“真”标记的概率进行排序。

- TILDE重排序：传统的查询似然模型（Santos等，2020；Zhuang等，2021）基于前序词元的似然度计算查询词的条件概率，但效率欠佳。TILDE（Zhuang和Zuccon，2021a,b）则独立处理每个查询词，并预测整个词汇表中各个词元的概率。通过在索引阶段预处理候选文档，只需对各文档中与查询词对应的预计算对数概率求和，即可实现快速重排序。TILDEv2仅索引文档中实际出现的词元，并采用NCE损失和文档扩展技术，进一步提升了效率并大幅缩减了索引规模。

我们的实验在MS MARCO段落排名数据集上进行（Bajaj等人，2016）。我们遵循并修改了PyGaggle（Nogueira等人，2020）和TILDE提供的实现，使用了monoT5、monoBERT、RankLLaMA和TILDEv2模型。重排序结果如表10所示。我们推荐monoT5作为一种在性能和效率之间取得平衡的综合性方法。RankLLaMA适合追求最佳性能，而TILDEv2则适用于在固定文档集上获得最快的处理体验。实验设置和结果的详细信息见附录A.4。

# 3.6 文档重新打包

后续流程（例如大语言模型响应生成）的性能可能会受到文档提供顺序的影响。为解决这一问题，我们在重排序后的工作流中引入了一个紧凑的重新打包模块，该模块采用三种重新打包方法：“正向”、“反向”

以及 “两侧” 策略。“正向” 策略按重排序阶段的相关性分数降序重新打包文档，而 “反向” 策略则按升序排列。受(Liu et al., 2024a)的启发，该研究认为当相关信息被置于输入信息的开头或结尾时，可实现最优性能，因此我们也纳入了 “两侧” 选项。

由于重打包方法主要影响后续模块，我们在第4节通过与其他模块组合测试来选择最佳重打包方法。这里，我们选择 “sides” 作为默认重打包方法。

# 3.7 总结

检索结果可能包含冗余或不必要信息，从而阻碍大语言模型生成准确的回答。此外，冗长的提示也会降低推理速度。因此，在RAG流程中，实现检索文档高效摘要化的方法至关重要。

摘要任务可分为抽取式和抽象式两种。抽取式方法将文本分割为句子，然后根据重要性进行评分和排序。抽象式文本压缩器则从多个文档中综合信息，以重新表述并生成连贯的摘要。这些任务可以是基于查询的，也可以是非基于查询的。在本文中，由于RAG检索的是与查询相关的信息，我们将只关注基于查询的方法。

- Recomp: Recomp (Xu et al., 2023) 拥有抽取式和抽象式压缩器。抽取式压缩器选择有用的句子，而抽象式压缩器则综合多个文档中的信息。

- LongLLMLingua: LongLLMLingua（Jiang等人，2023b）通过聚焦与查询相关的关键信息，改进了LLMLingua。

我们在三个基准数据集上评估了这些方法：NQ、TriviaQA 和 HotpotQA。不同摘要方法的比较结果如表11所示。我们推荐使用 Recomp，因为它表现出色。LongLLMLingua 表现不佳，但由于未在这些实验数据集上训练，因此展现出更好的泛化能力。因此，我们将其视为一种替代方法。有关实现细节及非基于查询的方法的讨论，请参见附录 A.5。

# 3.8 生成器微调

在本节中，我们专注于对生成器进行微调，而将检索器的微调留待未来。探索。我们旨在研究微调的影响，特别是相关或不相关上下文对生成器性能的影响。

在形式化定义中，我们将 x 记为输入到 RA G 系统的查询，将 D 记为该输入的上下文。生成器的微调损失是真实输出 y 的负对数似然。

为探究微调的影响，尤其是相关与不相关上下文的作用，我们将 $d_{gold}$ 定义为与查询相关的上下文，将 $d_{random}$ 定义为随机检索到的上下文。通过改变D的构成，我们对模型进行训练，具体如下：

- $D_{g}$ : 增强上下文由与查询相关的文档组成, 表示为 $D_{g} = \{d_{gold}\}$ 。

- $D_r$ 上下文包含一个随机采样的文档，记作 $D_r = \{d_{random}\}$ 。

- $D_{gr}$ : 增强的上下文包括一个相关文档和一个随机选择的文档, 记作

$$
D _ {g r} = \{d _ {g o l d}, d _ {r a n d o m} \} _ {\circ}
$$

- $D_{gg}$ 增强上下文由两份与查询相关的文档副本组成，记为 $D_{gg} = \{d_{gold}, d_{gold}\}$ 。

我们将未微调的基础语言模型生成器记作 $M_b$ ，并将基于相应 $\mathcal{D}$ 微调得到的模型分别记作 $M_g$ 、 $M_r$ 、 $M_{gr}$ 、 $M_{gg}$ 。我们使用多个问答和阅读理解数据集对模型进行了微调。由于问答任务的答案相对较短，我们采用真实答案覆盖率作为评估指标。具体而言，我们对精确匹配（EM）分数采取了一种更宽松的计算方式，即根据模型输出中是否包含标准答案来评估性能。我们选择 Llama-2-7B（Touvron et al., 2023b）作为基础模型。与训练类似，我们使用 $D_g$ 、 $D_r$ 、 $D_{gr}$ 和 $D_\emptyset$ 在验证集上评估所有训练好的模型，其中 $D_\emptyset$ 表示不进行检索的推理。图3展示了我们的主要结果。使用相关文档和随机文档混合训练（ $M_{gr}$ ）的模型，无论在提供标准上下文还是混合上下文时都表现最佳。这表明在训练中混合相关和随机上下文，既能增强生成器对无关信息的鲁棒性，又能确保有效利用相关上下文。因此，我们将训练中补充少量相关文档和随机选择文档的做法确定为最佳方法。详细的数据集信息、超参数和实验结果见附录 A.6。

# 4 寻找最佳RAG实践

以下章节我们将研究实施RAG的最佳实践。首先，我们为每个模块应用了第3节中确定的默认实践。按照图1所示的工作流程，我们依次优化各个模块，并从备选方案中选择最有效的选项。这一迭代过程持续进行，直到我们确定实现最终摘要生成模块的最佳方法。根据第3.8节，我们采用经过微调的Llama2-7B-Chat模型作为生成器，微调时每个查询通过添加少量随机选择的相关文档进行增强。我们使用Milvus构建了一个向量数据库，其中包含1000万条英文维基百科文本和400万条医疗数据文本。我们还研究了移除查询分类、重排序和摘要生成模块的影响，以评估它们的贡献。

# 4.1 综合评估

我们针对多种NLP任务与数据集，系统评估了RAG系统的性能。具体包括：(I)常识推理；(II)事实验证；(III)开放域问答；(IV)多跳问答；(V)医学问答。关于各任务及其对应数据集的详细信息，请参阅附录A.7。此外，我们基于这些数据集的子集，采用RAGAs（Shahul等，2023）推荐指标（包含忠实度、上下文相关性、答案相关性、答案正确性）评估了RAG能力。同时，我们通过计算检索文档与标准文档之间的余弦相似度，衡量了检索相似性。

我们使用准确率作为常识推理、事实核查和医学问答任务的评估指标。对于开放域问答和多跳问答，我们采用了令牌级F1分数和精确匹配（EM）分数。最终的RAG分数通过平均上述五项RAG能力来计算。所有任务均一致使用了第3节中构建的相同语料库。我们遵循Trivedi等人（2022）的方法，从每个数据集中子采样最多500个示例。

# 4.2 结果与分析

基于表1所示的实验结果，得出以下关键见解：

- 查询分类模块：该模块对于效果和效率都至关重要，引导

导致整体得分平均从0.428提高到0.443，并将每次查询的延迟时间从16.41秒减少到11.58秒。查询分类方法根据查询中信息的完整性，区分需要检索操作的查询和不需要检索操作的查询。这种选择性检索策略避免了不必要的操作，显著提升了性能和响应时间。

- 检索模块：稠密检索与经典的BM25算法相结合，由于两者的优势互补，展现出卓越的性能。稠密检索擅长识别语义关系（例如将“坏蛋”和“恶棍”这类词语联系起来），但在处理罕见术语和未登录词（OOV）时存在困难。而BM25善于匹配特定词汇，弥补了这些不足。这种混合方法平衡了两种方法的优势，增强了检索的鲁棒性。此外，使用生成的伪文档可以最小化查询与相关文档之间的语义不匹配。虽然“Hybrid with HyDE”方法取得了最高的RAG分数0.58，但其计算成本为每次查询11.71秒。在实际应用中，推荐使用“Hybrid”或“Original”方法，因为它们保持了相当的性能，同时降低了延迟。

- 重排序模块：重排序对保持高质量结果至关重要，若缺少该环节则性能会出现下降。在基于DLM的重排序模型中，monoT5显著优于monoBERT和RankLLaMA。这一优势可归因于monoT5更庞大的参数集与更广泛的训练数据，以及其编码器-解码器架构，与仅解码器的LLaMA模型相比，它能提供更强的自然语言理解能力。Monot5在提升检索文档相关性方面的有效性，印证了重排序对于改善生成回复质量的必要性。

- 重新打包模块：反向配置展现出优越的性能，获得了0.560的RAG分数。这凸显了将更相关的上下文放置在靠近查询的位置以获得最佳结果的重要性。

- 摘要模块：Recomp抽取式摘要方法表现出优于LongLLMLingua的性能，后者是一种基

<table><tr><td rowspan="2">Method</td><td>Commonsense</td><td>Fact Check</td><td colspan="3">ODQA</td><td colspan="3">Multihop</td><td>Med</td><td>RAG</td><td colspan="3">Avg.</td></tr><tr><td>Acc</td><td>Acc</td><td>EM</td><td>F1</td><td>Score</td><td>EM</td><td>F1</td><td>Score</td><td>Acc</td><td>Score</td><td>Score</td><td>F1</td><td>Latency</td></tr><tr><td colspan="14">without retrieval</td></tr><tr><td>+ baseline</td><td>0.537</td><td>0.560</td><td>0.373</td><td>0.413</td><td>0.428</td><td>0.167</td><td>0.173</td><td>0.182</td><td>0.360</td><td>-</td><td>0.351</td><td>0.292</td><td>1.27</td></tr><tr><td colspan="14">classification module, Hybrid with HyDE, monoT5, sides, Recomp</td></tr><tr><td>w/o classification</td><td>0.719</td><td>0.505</td><td>0.391</td><td>0.450</td><td>0.478</td><td>0.212</td><td>0.255</td><td>0.254</td><td>0.528</td><td>0.540</td><td>0.422</td><td>0.353</td><td>16.58</td></tr><tr><td>+ classification</td><td>0.727</td><td>0.595</td><td>0.393</td><td>0.450</td><td>0.479</td><td>0.207</td><td>0.257</td><td>0.254</td><td>0.460</td><td>0.580</td><td>0.443</td><td>0.353</td><td>11.71</td></tr><tr><td colspan="14">with classification, retrieval module, monoT5, sides, Recomp</td></tr><tr><td>+ HyDE</td><td>0.718</td><td>0.595</td><td>0.320</td><td>0.373</td><td>0.380</td><td>0.170</td><td>0.213</td><td>0.222</td><td>0.400</td><td>0.545</td><td>0.398</td><td>0.293</td><td>11.58</td></tr><tr><td>+ Original</td><td>0.721</td><td>0.585</td><td>0.300</td><td>0.350</td><td>0.363</td><td>0.153</td><td>0.197</td><td>0.206</td><td>0.390</td><td>0.486</td><td>0.383</td><td>0.273</td><td>1.44</td></tr><tr><td>+ Hybrid</td><td>0.718</td><td>0.595</td><td>0.347</td><td>0.397</td><td>0.418</td><td>0.190</td><td>0.240</td><td>0.233</td><td>0.750</td><td>0.498</td><td>0.429</td><td>0.318</td><td>1.45</td></tr><tr><td>+ Hybrid + HyDE</td><td>0.727</td><td>0.595</td><td>0.393</td><td>0.450</td><td>0.479</td><td>0.207</td><td>0.257</td><td>0.254</td><td>0.460</td><td>0.580</td><td>0.443</td><td>0.353</td><td>11.71</td></tr><tr><td colspan="14">with classification, Hybrid with HyDE, reranking module, sides, Recomp</td></tr><tr><td>w/o reranking</td><td>0.720</td><td>0.591</td><td>0.365</td><td>0.429</td><td>0.435</td><td>0.211</td><td>0.260</td><td>0.253</td><td>0.512</td><td>0.530</td><td>0.430</td><td>0.334</td><td>10.31</td></tr><tr><td>+ monoT5</td><td>0.727</td><td>0.595</td><td>0.393</td><td>0.450</td><td>0.479</td><td>0.207</td><td>0.257</td><td>0.253</td><td>0.460</td><td>0.580</td><td>0.443</td><td>0.353</td><td>11.71</td></tr><tr><td>+ monoBERT</td><td>0.723</td><td>0.593</td><td>0.383</td><td>0.443</td><td>0.463</td><td>0.217</td><td>0.259</td><td>0.253</td><td>0.482</td><td>0.551</td><td>0.438</td><td>0.351</td><td>11.65</td></tr><tr><td>+ RankLLaMA</td><td>0.723</td><td>0.597</td><td>0.382</td><td>0.443</td><td>0.459</td><td>0.197</td><td>0.240</td><td>0.237</td><td>0.454</td><td>0.558</td><td>0.431</td><td>0.342</td><td>13.51</td></tr><tr><td>+ TILDEV2</td><td>0.725</td><td>0.588</td><td>0.394</td><td>0.456</td><td>0.473</td><td>0.209</td><td>0.255</td><td>0.249</td><td>0.486</td><td>0.536</td><td>0.440</td><td>0.355</td><td>11.26</td></tr><tr><td colspan="14">with classification, Hybrid with HyDE, monoT5, repacking module, Recomp</td></tr><tr><td>+ sides</td><td>0.727</td><td>0.595</td><td>0.393</td><td>0.450</td><td>0.479</td><td>0.207</td><td>0.257</td><td>0.253</td><td>0.460</td><td>0.580</td><td>0.443</td><td>0.353</td><td>11.71</td></tr><tr><td>+ forward</td><td>0.722</td><td>0.599</td><td>0.379</td><td>0.437</td><td>0.458</td><td>0.215</td><td>0.260</td><td>0.254</td><td>0.472</td><td>0.542</td><td>0.437</td><td>0.349</td><td>11.68</td></tr><tr><td>+ reverse</td><td>0.728</td><td>0.592</td><td>0.387</td><td>0.445</td><td>0.473</td><td>0.219</td><td>0.263</td><td>0.260</td><td>0.532</td><td>0.560</td><td>0.446</td><td>0.354</td><td>11.70</td></tr><tr><td colspan="14">with classification, Hybrid with HyDE, monoT5, reverse, summarization module</td></tr><tr><td>w/o summarization</td><td>0.729</td><td>0.591</td><td>0.402</td><td>0.457</td><td>0.468</td><td>0.205</td><td>0.252</td><td>0.245</td><td>0.528</td><td>0.533</td><td>0.441</td><td>0.355</td><td>10.97</td></tr><tr><td>+ Recomp</td><td>0.728</td><td>0.592</td><td>0.387</td><td>0.445</td><td>0.473</td><td>0.219</td><td>0.263</td><td>0.260</td><td>0.532</td><td>0.560</td><td>0.446</td><td>0.354</td><td>11.70</td></tr><tr><td>+ LongLLMLingua</td><td>0.713</td><td>0.581</td><td>0.362</td><td>0.423</td><td>0.432</td><td>0.199</td><td>0.245</td><td>0.245</td><td>0.530</td><td>0.539</td><td>0.426</td><td>0.334</td><td>16.17</td></tr></table>


表1：搜索最优RAG实践的结果。用方框标出的模块正在研究中以确定最佳方法。带下划线的方法表示所选实现。对于两个QA任务，ODQA和MultiHop，我们使用GPT同时对其进行评分。“Avg”（平均分数）是根据所有任务的Acc、EM和RAG分数计算得出的，而平均延迟以每次查询的秒数衡量。最佳分数以粗体突出显示。


stractive摘要方法。我们的实验表明，LongL LMLingua因其重写方法偶尔会扭曲语义并产生不连贯的内容。而Recomp则保持了原始内容的完整性，因此更适合RAG应用。尽管移除摘要模块能够以更低的延迟获得可比较的结果，但对于需解决生成器最大长度限制的关键场景，Recomp仍是首选。在时间敏感的应用中，移除摘要功能可以有效缩短响应时间。

实验结果证明，每个模块都对RAG系统的整体性能有独特贡献。查询分类模块提高了准确性并降低了延迟，而检索和重排序模块显著提升了系统处理多样化查询的能力。重打包和摘要模块进一步优化了系统输出，确保

在不同任务中生成高质量响应。

# 5 讨论

# 5.1 实施RAG的最佳实践

根据我们的实验结果，我们建议采用两种不同的方案或实践来实施RAG系统，每种方案都根据特定需求进行定制：一种侧重于最大化性能，另一种则侧重于在效率与效能之间取得平衡。

最佳性能实践：为达到最高性能，建议结合查询分类模块，使用“Hybrid with HyDE”方法进行检索，采用monoT5进行重排序，选择Reverse进行重新打包，并利用Recomp进行摘要生成。此配置产生了0.483的最高平均得分，尽管计算过程十分密集。

平衡效率实践：为实现性能与效率之间的平衡，建议融合查询分类

模块中，检索部分采用混合方法，重排序使用TILDEv2，重打包选择Reverse，摘要部分则使用Recomp。考虑到检索模块占用了系统大部分的处理时间，在保持其他模块不变的情况下，改用混合方法可大幅降低延迟，同时保持可比性能。

# 5.2 最佳实践的推广

尽管上述最佳实践在我们的实验中展现出强大的性能，但我们承认它们可能并非在所有任务和情境下都普遍最优。因此，我们强调全面评估框架的重要性，该框架评估系统在通用、领域特定和任务特定能力方面的表现，以及确定最有效实践的三步策略：

- 候选实现的实证比较：对于每个模块，我们比较多种候选方法以确定性能最佳的选项。

- 模块集成：在为每个模块选定最佳方法后，我们评估它们集成到完整工作流程中时的交互情况。

- 模块组合的评估：最后，我们评估不同模块组合的性能，以识别提高系统效率和有效性的机会。

# 5.3 多模态扩展

我们已经将RAG扩展到了多模态应用中。具体而言，我们已将文本到图像和图像到文本的检索能力集成到系统中，并使用大量配对的图像与文本描述作为检索源。如图4所示，当用户查询与存储图像的文本描述高度匹配时，文本到图像能力会加速图像生成过程（即“检索即生成”策略）；而当用户提供图像并就输入图像进行对话时，图像到文本功能便会发挥作用。这些多模态RAG能力具有以下优势：

- 扎实性：检索方法从经过验证的多模态材料中提供信息，从而确保真实性和具体性。相比之下，即时生成依赖于模型生成新内容，这有时可能导致事实错误或不准确。

- 效率：检索方法通常更高效，尤其是当答案已经存在于存储材料中时。相反，生成方法可能需要更多计算资源来产生新内容，特别是对于图像或长篇文本。

- 可维护性：生成模型通常需要细致的微调以适应新的应用场景。相较之下，基于检索的方法仅需扩大检索源的规模并提升其质量，即可改进以满足新需求。

我们采用了（Koh et al., 2024）的实验设置。具体而言，我们使用 PartiPrompts 数据集来引导稳定扩散模型生成图像，并从 CC3M 数据集中检索图像。然后，我们使用 openai/clip-vit -large-patch14 $^{2}$ 计算提示与两类图像（PRO2GEN 和 PRO2RET）之间的 CLIP 相似度，并记录两种方法的耗时。图 5 体现了“检索即生成”策略的根基性，因为生成模型不可控且可能缺乏相关知识。如表 15 所示，“检索即生成”策略大幅减少了时间消耗，同时保持了图像质量，并且我们可以通过扩展搜索来源来提升检索性能，这体现了该策略的高效性与可维护性。

此外，我们计划将这一策略的应用扩展到包括视频和语音在内的其他模态，同时探索高效且有效的跨模态检索技术。

# 6 结论

在本研究中，我们旨在确定实施检索增强生成的最佳实践，以提高大型语言模型生成内容的质量和可靠性。我们系统评估了RAG框架内每个模块的一系列潜在解决方案，并为每个模块推荐了最有效的方法。此外，我们引入了针对RAG系统的综合评估基准，并开展了大量实验，以确定各种替代方案中的最佳实践。我们的发现不仅有助于更深入地理解检索增强生成系统，也为未来的研究奠定了基础。

# 局限性

我们评估了各种微调大语言模型生成器方法的影响。先前的研究已经证明了联合训练检索器和生成器的可行性，我们希望在未来的工作中探索这一可能性。本研究采用模块化设计原则，旨在简化最优RAG实现方式的搜索过程，从而降低复杂度。由于构建向量数据库和进行实验所需的巨大成本，我们的评估仅限于研究分块模块中代表性分块技术的有效性和影响。进一步探索不同分块技术对整个RAG系统的影响将是一个有趣的方向。虽然我们讨论了RAG在自然语言处理领域的应用，并将其范围扩展到图像生成，但未来一个诱人的探索方向是将这项研究拓展至语音和视频等其他模态。

Nick Craswell、Bhaskar Mitra、Emine Yilmaz、Daniel Fernando Campos 和 Ellen M. Voorhees. 2020. TR EC 2019 深度学习赛道概述. ArXiv, abs/2003.07820.

Nick Craswell、Bhaskar Mitra、Emine Yilmaz、Daniel Fernando Campos 和 Ellen M. Voorhees. 2021. TR EC 2020 深度学习赛道概述. ArXiv, abs/2102.07662.

Luyu Gao, Xueguang Ma, Jimmy Lin, 和 Jamie Callan. 2022. 无需相关性标签的精确零样本密集检索. arXiv preprint arXiv:2212.10496.

Yunfan Gao, Yun Xiong, Xinyu Gao, Kangxiang Jia, Jinliu Pan, Yuxi Bi, Yi Dai, Jiawei Sun 与 Haofen Wang. 2023. 面向大语言模型的检索增强生成：一项综述. arXiv preprint arXiv:2312.10997.

Michael Günther、Jackmin Ong、Isabelle Mohr、Ala eddine Abdessalem、Tanguy Abel、Mohammad Kali m Akram、Susana Guzman、Georgios Mastrapas、S aba Sturua、Bo Wang等人。2023。Jina嵌入2：面向长文档的8192令牌通用文本嵌入。
arXiv preprint arXiv:2310.19923

# 参考文献



Akari Asai, Zeqiu Wu, Yizhong Wang, Avirup Sil 和 Hannaneh Hajishirzi. 2023. Self-rag: 通过自我反思学习检索、生成和批评.
arXiv preprint arXiv:2310.11511





Payal Bajaj, Daniel Campos, Nick Craswell, Li Deng, Jianfeng Gao, Xiaodong Liu, Rangan Majumder, Andrew McNamara, Bhaskar Mitra, Tri Nguyen, 等. 2016. Ms marco: A human generated machine reading comprehension dataset. arXiv preprint arXiv:1611.09268.





Jonathan Berant、Andrew Chou、Roy Frostig 和 Percy Liang。2013 年。《基于问答对的 Freebase 语义解析》。Empirical Methods in Natural Language Processing, Empirical Methods in Natural Language Processing





Deng Cai、Yan Wang、Lemao Liu与Shuming Shi, 2022. 检索增强文本生成技术的最新进展. 刊于 Proceedings of the 45th international ACM SIGIR conference on research and development in information retrieval, 第3417至3419页.





Peter Clark、Isaac Cowhey、Oren Etzioni、Tushar K hot、Ashish Sabharwal、Carissa Schoenick 和 Oyvin d Tafjord. 2018. 认为您已经解决了问答问题？试试 ARC，AI2 推理挑战。ArXiv, abs/1803.05457.





Mike Conover、Matt Hayes、Ankit Mathur、Jianwei Xie、Jun Wan、Sam Shah、Ali Ghodsi、Patrick Wendell、Matei Zaharia、Reynold Xin. 2023. Free Dolly：介绍世界首个真正开放的指令微调大语言模型。





凯尔文·古、肯顿·李、佐拉·董、帕努蓬·帕苏帕特和张明伟。2020年。《Realm：检索增强语言模型预训练》。ArXiv，abs/2002.08909。





Dan Hendrycks、Collin Burns、Steven Basart、Andy Zou、Mantas Mazeika、Dawn Song 与 Jacob Steinhardt。2020。大规模多任务语言理解的测量。
Cornell University - arXiv, Cornell University - arXiv





Xanh Ho、A. Nguyen、Saku Sugawara 和 Akiko Aizawa。2020 年。构建一个多跳问答数据集以全面评估推理步骤。ArXiv，abs/2011.01060。





J. Edward Hu, Yelong Shen, Phillip Wallis, Zeyuan Alen-Zhu, Yuanzhi Li, Shean Wang, Weizhu Chen. 2021. Lora: 大型语言模型的低秩适应. ArXiv, abs/2106. 09685.





黄易正和黄吉米. 2024. 大语言模型检索增强文本生成综述. arXiv preprint arXiv:2404.10981.





Gautier Izacard、Mathilde Caron、Lucas Hosseini、Sebastian Riedel、Piotr Bojanowski、Armand Joulin 和 Edouard Grave。2021。基于对比学习的无监督密集信息检索。arXiv preprint arXiv:2112.09118。





Gautier Izacard、Patrick Lewis、Maria Lomeli、Luc as Hosseini、Fabio Petroni、Timo Schick、Jane A. Yu、Armand Joulin、Sebastian Riedel 和 Edouard Grave。2022 年。基于检索增强语言模型的少样本学习。ArXiv，abs/2208.03299。





Huiqiang Jiang, Qianhui Wu, Chin-Yew Lin, Yuqing Yang, 和 Lili Qiu. 2023a. Llmlingua: 压缩提示词以加速大型语言模型推理.
arXiv preprint arXiv:2310.05736.





Huiqiang Jiang, Qianhui Wu, Xufang Luo, Dongsheng Li, Chin-Yew Lin, Yuqing Yang, 和 Lili Qiu. 2023b. Longllmlingua: 通过提示压缩在长上下文场景中加速和增强LLMs。arXiv preprint arXiv:2310.06839。





Qiao Jin、Bhuwan Dhingra、Zhengping Liu、William W. Cohen 和 Xinghua Lu. 2019. Pubmedqa: 一个用于生物医学研究问答的数据集。收录于 Conference on Empirical Methods in Natural Language Processing。





Mandar Joshi、Eunsol Choi、Daniel S. Weld 和 Luke Zettlemoyer。2017 年。《Triviaqa：一个大规模远程监督的阅读理解挑战数据集》。ArXiv，abs/1705.03551。





Gangwoo Kim, Sungdong Kim, Byeongguk Jeon, Joon suk Park, Jaewoo Kang. 2023. 澄清树：利用检索增强的大规模语言模型回答模糊问题. arXiv preprint arXiv:2310.14696.





Tomá Kočiský、Jonathan Schwarz、Phil Blunsom、Chris Dyer、Karl Moritz Hermann、Gábor Melis 和 Edward Grefenstette. 2018. NarrativeQA 阅读理解挑战. Transactions of the Association for Computational Linguistics, 6:317–328.





Jing Yu Koh、Daniel Fried 和 Russ R Salakhutdinov. 2024. 使用多模态语言模型生成图像.
Advances in Neural Information Processing Systems, 36.





Tom Kwiatkowski, Jennimaria Palomaki, Olivia Redfield, Michael Collins, Ankur P. Parikh, Chris Alberti, Danielle Epstein, Illia Polosukhin, Jacob Devlin, Kenton Lee, Kristina Toutanova, Llion Jones, Matthew Kelcey, Ming-Wei Chang, Andrew M. Dai, Jakob Uszkoreit, Quoc V. Le, 和 Slav Petrov. 2019. 自然问题：问答研究的基准. Transactions of the Association for Computational Linguistics, 7:453–466.





Huayang Li, Yixuan Su, Deng Cai, Yan Wang, 和 Le mao Liu. 2022. 检索增强文本生成综述.
arXiv preprint arXiv:2202.01110.





李泽涵，张鑫，张彦钊，龙定坤，谢鹏俊和张梅山。2023.面向通用文本嵌入的多阶段对比学习。arXiv preprint arXiv:2308.03281





Jimmy Lin, Xueguang Ma, Sheng-Chieh Lin, Jheng-Hong Yang, Ronak Pradeep 和 Rodrigo Nogueira. 2021 a. Pyserini: 一个用于稀疏和稠密表示的可复现信息检索研究的 Python 工具包. 见 Proceedings of the 44th International ACM SIGIR Conference on Research and Development in Information Retrieval, 第 2356–236 2 页。





Stephanie Lin、Jacob Hilton 和 Owain Evans. 2021b. TruthfulQA: 衡量模型如何模仿人类的虚假陈述. arXiv preprint arXiv:2109.07958





Xi Victoria Lin, Xilun Chen, Mingda Chen, Weijia Shi, Maria Lomeli, Rich James, Pedro Rodriguez, Jacob Kahn, Gergely Szilvasy, Mike Lewis, Luke Zettlemoyer 和 Scott Yih. 2023. Ra-dit: 检索增强的双指令调优. ArXiv, abs/2310.01352.





杰里·刘. 2022. LlamaIndex.





Nelson F Liu、Kevin Lin、John Hewitt、Ashwin Paranjape、Michele Bevilacqua、Fabio Petroni 和 Percy Liang。2024a。迷失在中间：语言模型如何利用长上下文。Transactions of the Association for Computational Linguistics, 12:157–173。





Wenhao Liu, Xiaohua Wang, Muling Wu, Tianlong Li, Changze Lv, Zixuan Ling, Jianhao Zhu, Cenyuan Zhang, Xiaoqing Zheng, 和 Xuanjing Huang. 2023. 通过表征工程将大型语言模型与人类偏好对齐. arXiv preprint arXiv:2312.15997.





Zihan Liu、Wei Ping、Rajarshi Roy、Peng Xu、Chankyu Lee、Mohammad Shoeybi 和 Bryan Catanzaro. 2024b. Chatqa: 在对话式问答和 RAG 上超越 GPT-4.





LlamaIndex。Llamaindex 网站。https://www.llamaindex.com。访问日期：2024-06-08。





Hongyin Luo, Yung-Sung Chuang, Yuan Gong, Tian-hua Zhang, Yoon Kim, Xixin Wu, Danny Fox, Helen M. Meng 和 James R. Glass. 2023. Sail: 搜索增强的指令学习. 见 Conference on Empirical Methods in Natural Language Processing.





马心蓓, 龚烨筠, 何鹏程, 赵海, 段楠. 2023a. 面向检索增强大语言模型的查询重写。arXiv preprint arXiv:2305.14283





马学光、王亮、杨楠、魏福如 和 Jimmy Lin. 2023b.
面向多阶段文本检索的LLaMA微调.
arXiv preprint arXiv:2310.08319





Todor Mihaylov、Peter Clark、Tushar Khot 和 Ashish Sabharwal. 2018. 盔甲能导电吗？一个用于开卷问答的新数据集。在 Proceedings of the 2018 Conference on Empirical Methods in Natural Language Processing 中。





罗德里戈·诺盖拉、蒋志英与吉米·林. 2020年. 基于预训练序列到序列模型的文档排序.
arXiv preprint arXiv:2003.06713.





Rodrigo Nogueira、Wei Yang、Kyunghyun Cho 和 Jimmy Lin. 2019. 使用BERT进行多阶段文档排序. arXiv preprint arXiv:1910.14424





OpenAI. 2023. GPT-4技术报告. CoRR, abs/2303.08774.





Long Ouyang, Jeff Wu, Xu Jiang, Diogo Almeida, Car roll L. Wainwright, Pamela Mishkin, Chong Zhang, Sandhini Agarwal, Katarina Slama, Alex Ray, John Schulman, Jacob Hilton, Fraser Kelton, Luke Miller, Maddie Simens, Amanda Askell, Peter Welinder, Paul Christiana no, Jan Leike, 和 Ryan Lowe. 2022. 基于人类反馈训练语言模型以遵循指令. 在





Proceedings of the Conference on Neural Information Processing Systems (NeurIPS 2022) 中。





Ofir Press、Muru Zhang、Sewon Min、Ludwig Schmidt、Noah A. Smith 和 Mike Lewis. 2022. 测量并缩小语言模型中的组合性差距。





Rafael Rafailov、Archit Sharma、Eric Mitchell、Stefano Ermon、Christopher D Manning 和 Chelsea Finn. 2023. 直接偏好优化：你的语言模型其实是一个奖励模型. arXiv preprint arXiv:2305.18290.





Pranav Rajpurkar, Jian Zhang, Konstantin Lopyrev 和 Percy Liang. 2016. Squad: 用于文本机器理解的 100,000+ 个问题. arXiv preprint arXiv:1606.05250.





Cicero Nogueira dos Santos、Xiaofei Ma、Ramesh N allapati、Zhiheng Huang 和 Bing Xiang. 2020. Beyond [cls] through ranking by generation. arXiv preprint arXiv:2010.03073.





Kunal Sawarkar, Abhilasha Mangal, 和 Shivam Raj Solanki. 2024. 混合RAG：通过语义搜索和混合查询检索器提高RAG（检索增强生成）准确性.
arXiv preprint arXiv:2404.07220





ES Shahul, Jithin James, Luis Espinosa Anke, 与 Steven Schockaert. 2023. Ragas: 检索增强生成的自动评估. 载于 Conference of the European Chapter of the Association for Computational Linguistics.





Weijia Shi, Sewon Min, Michihiro Yasunaga, Min-joon Seo, Rich James, Mike Lewis, Luke Zettlemoyer 和 Wen-tau Yih. 2023. Replug: 检索增强的黑盒语言模型. arXiv preprint arXiv:2301.12652





Ivan Stelmakh, Yi Luan, Bhuwan Dhingra, 和 Ming-Wei Chang. 2022. Asqa: 事实性问题遇上长篇答案. ArXiv, abs/2204.06092.





Nandan Thakur、Nils Reimers、Andreas Rücklé、Ab hishek Srivastava 和 Iryna Gurevych。2021 年。Beir：信息检索模型零样本评估的异构基准。
arXiv preprint arXiv:2104.08663。





James Thorne、Andreas Vlachos、Christos Christodoulopoulos 和 Arpit Mittal。2018 年。Fever：一个用于事实提取与验证的大规模数据集。ArXiv，abs/18 03.05355。





Hugo Touvron、Thibaut Lavril、Gautier Izacard、Xavier Martinet、Marie-Anne Lachaux、Timothée Lacroix、Baptiste Rozière、Naman Goyal、Eric Hambro、Faisal Azhar等人。2023a。LLaMA：开放且高效的基础语言模型。arXiv preprint arXiv:2302.13971





Hugo Touvron, Louis Martin, Kevin R. Stone, Peter Albert, Amjad Almahairi, Yasmine Babaei, Nikolay Bash lykov, Soumya Batra, Prajjwal Bhargava, Shruti Bhosa le, Daniel M. Bikel, Lukas Blecher, Cristian Cantón Ferrer, Moya Chen, Guillem Cucurull, David Esiobu, Jude Fernandes, Jeremy Fu, Wenyin Fu, Brian Fuller, Cynthia Gao, Vedanuj Goswami, Naman Goyal, Anthony S. Hartshorn, Saghar Hosseini, Rui Hou, Hakan Inan, Marcin Kardas, Viktor Kerkez, Madian Khabsa, Isabel M. Kloumann, A. V. Korenev, Punit Singh Koura, Marie-Anne Lachaux, Thibaut Lavril, Jenya Lee, Diana Lis kovich, Yinghai Lu, Yuning Mao, Xavier Martinet, To dor Mihaylov, Pushkar Mishra, Igor Molybog, Yixin Nie, Andrew Poulton, Jeremy Reizenstein, Rashi Rungta , Kalyan Saladi, Alan Schelten, Ruan Silva, Eric Micha el Smith, R. Subramanian, Xia Tan, Binh Tang, Ross Taylor, Adina Williams, Jian Xiang Kuan, Puxin Xu, Zhengxu Yan, Iliyan Zarov, Yuchen Zhang, Angela Fan, Melanie Kambadur, Sharan Narang, Aurelien Rodriguez , Robert Stojnic, Sergey Edunov 和 Thomas Scialom . 2023b. Llama 2: 开放式基础与微调聊天模型. ArXiv , abs/2307.09288.





Harsh Trivedi, Niranjan Balasubramanian, Tushar Kho t, and Ashish Sabharwal. 2022. Musique: 通过单跳问题组合实现多跳问题.
Transactions of the Association for Computational Linguistics, 第539–554页.





Liang Wang、Nan Yang、Xiaolong Huang、Binxing Jiao、Linjun Yang、Daxin Jiang、Rangan Majumder 和 Furu Wei. 2022. 通过弱监督对比预训练的文本嵌入. arXiv preprint arXiv:2212.03533.





Liang Wang、Nan Yang 与 Furu Wei. 2023a. Query2
doc: 基于大语言模型的查询扩展.
arXiv preprint arXiv:2303.07678





Xiaohua Wang, Yuliang Yan, Longtao Huang, Xiaoqing Zheng, Xuan-Jing Huang. 2023b. 基于贝叶斯序列估计的生成式大语言模型幻觉检测. 收录于 Proceedings of the 2023 Conference on Empirical Methods in Natural Language Processing, 第15361-15371页。





Zhiruo Wang、Jun Araki、Zhengbao Jiang、Md Riz wan Parvez 和 Graham Neubig. 2023c. 学习过滤上下文以用于检索增强生成. arXiv
preprint arXiv:2311.08377





Shitao Xiao, Zheng Liu, Peitian Zhang, 和 Niklas Muenighoff. 2023. C-pack: 推进通用中文嵌入的打包资源. Preprint, arXiv:2309.07597.





Fangyuan Xu、Weijia Shi 和 Eunsol Choi. 2023. Re-comp: 通过压缩与选择性增强改进检索增强语言模型。arXiv preprint arXiv:2310.04408





Zhilin Yang, Peng Qi, Saizheng Zhang, Yoshua Bengio, William W Cohen, Ruslan Salakhutdinov 与 Christopher D Manning. 2018. Hotpotqa: 一个面向多样化、可解释的多跳问答数据集.
arXiv preprint arXiv:1809.09600.





郑源, 袁弘毅, 谭传奇, 王伟, 黄松芳, 黄飞. 2023. RR HF: 通过排序响应轻松对齐语言模型与人类反馈. arXiv preprint arXiv:2304.05302.





Hamed Zamani 和 Michael Bendersky。2024 年。随机式 RAG：通过期望效用最大化实现端到端检索增强生成。





张灵熙，余悦，王宽，张超. 2024a. Arl2: 通过自引导自适应相关性标注对齐黑盒大语言模型的检索器. ArXiv, abs/2402.13542.





张培田, 肖世涛, 刘峥, 窦志成, 聂建云. 2023a. 检索任何内容以增强大型语言模型. arXiv preprint arXiv:2310.07554





Tianhua Zhang, Hongyin Luo, Yung-Sung Chuang, Wei Fang, Luc Gaitskell, Thomas Hartvigsen, Xixin Wu, Danny Fox, Helen M. Meng, 和 James R. Glass. 2023b. 可解释的统一语言检查. ArXiv, abs/2304.03728.





Tianjun Zhang、Shishir G. Patil、Naman Jain、Sheng Shen、Matei A. Zaharia、Ion Stoica 和 Joseph E. Gonzalez. 2024b. Raft: 将语言模型适配到特定领域的 RAG. ArXiv, abs/2403.10131.





Yue Zhang, Yafu Li, Leyang Cui, Deng Cai, Lemao Li u, Tingchen Fu, Xinting Huang, Enbo Zhao, Yu Zhang, Yulong Chen, et al. 2023c. AI海洋中的塞壬之歌：大型语言模型幻觉研究综述。arXiv preprint arXiv:2309.01219





赵鹏浩，张海林，余沁涵，王正仁，耿云腾，傅方成，杨玲，张文涛，崔斌. 2024. 面向AI生成内容的检索增强生成：一项综述.
arXiv preprint arXiv:2402.19473





Ruochen Zhao, Hailin Chen, Weishi Wang, Fangkai Jiao, Xuan Long Do, Chengwei Qin, Bosheng Ding, Xiaobao Guo, Minzhi Li, Xingxuan Li, 等. 2023a. 检索多模态信息以增强生成：综述. arXiv preprint arXiv:2303.10868.





Yao Zhao, Rishabh Joshi, Tianqi Liu, Misha Khalman, Mohammad Saleh 和 Peter J Liu. 2023b. SLIC-HF: 基于人类反馈的序列似然校准.
arXiv preprint arXiv:2305.10425.





Shengyao Zhuang、Hang Li 和 Guido Zuccon. 2021. 用于信息检索的深度查询似然模型. 载于 Advances in Information Retrieval: 43rd European Conference on IR Research, ECIR 2021, Virtual Event, March 28–April 1, 2021, Proceedings, Part II 43, 第463–470页. Springer.





Shengyao Zhuang 和 Guido Zuccon. 2021a. 利用上下文化的精确术语匹配与高效段落扩展的快速段落重排序。arXiv preprint arXiv:2108.08513。





Shengyao Zhuang 和 Guido Zuccon. 2021b. Tilde: 用于段落重排序的项独立似然模型. 收录于 Proceedings of the 44th International ACM SIGIR Conference on Research and Development in Information Retrieval, 第1483–1492页。



# A 实验细节

在本节中，我们提供每个模块的详细实验设置，涵盖数据集规格、训练参数以及任何附加的实验结果。

# A.1 查询分类

数据集为了开发查询分类器，我们构建了一个包含111K个样本的综合数据集，覆盖了15种不同类型的任务，其中64K个样本标记为“需要检索”，47K个样本标记为“无需检索”。该数据集源自多种专门来源，每个来源都为广泛的任务特定数据做出了贡献：

- 代码：code_alpaca_20k。

- 医学相关：medical_questions_pairs。

- 建议：oasst_quality_with_suggestions。

- 角色扮演：roleplay_alpaca。

- 重写：merge_rewrite_13.3k。

- 多任务：Databricks-Dolly-15K（Conover等人，2023年），涵盖的任务包括封闭式问答、分类、信息提取、摘要生成和写作。

对于这些数据集未能覆盖的其他任务，我们使用GPT-4生成了相应的样本。

我们选用BERT-base-multilingual-cased作为分类器，批次大小为16，学习率为1e-5。实验结果展示于表2。

<table><tr><td rowspan="2">Model</td><td colspan="4">Metrics</td></tr><tr><td>Acc</td><td>Prec</td><td>Rec</td><td>F1</td></tr><tr><td>BERT-base-multilingual</td><td>0.95</td><td>0.96</td><td>0.94</td><td>0.95</td></tr></table>


表2：查询分类器的结果。


# A.2 分块方法的实验细节

找到最佳分块大小需要在忠实度、相关性等指标之间取得平衡。忠实度衡量响应是幻觉生成还是与检索到的文本匹配。相关性衡量检索到的文本和响应是否与查询匹配。我们使用LlamaIndex(LlamaIndex)的评估模块来计算上述指标。对于嵌入，我们使用支持长输入长度的text-embedding-ada-002 $^{3}$ 模型。我们选择

分别使用 zephyr-7b-alpha $^{4}$ 和 gpt-3.5-turbo $^{5}$ 作为生成模型和评估模型。文本块重叠大小为 20 个标记。使用文档 lyft_2021 $^{6}$ 的前 60 页作为语料库，随后提示大语言模型根据所选语料库生成约 170 条查询。不同文本块大小的影响如表 3 所示。

分块技术 为展示先进分块技术的有效性，我们采用LLM-Embedder (Zhang et al., 2023a) 作为嵌入模型。较小的分块大小为175个token，较大的分块大小为512个token，分块重叠为20个token。诸如从小到大和滑动窗口等技术通过保持上下文来提升检索质量，确保检索到相关信息。详细结果见表4。

嵌入模型选择 用于RAG的嵌入模型需考虑查询与文本块间的语义空间匹配问题。我们采用FlagEmbedding $^{7}$ 评测模块，以命名空间-Pt/msmarco $^{8}$ 数据集作为查询集、命名空间-Pt/msmarco-corpus $^{9}$ 数据集作为语料库，选取合适的开源嵌入模型。如表5所示，LLM-Embedder（Zhang等，2023a）取得了与BAAI/bge-large-en（Xiao等，2023）相近的性能，但前者模型规模仅为后者的三分之一。因此我们选择LLM-Embedder构建向量数据库。

<table><tr><td rowspan="2">Chunk Size</td><td colspan="2">lyft_2021</td></tr><tr><td>Average Faithfulness</td><td>Average Relevancy</td></tr><tr><td>2048</td><td>80.37</td><td>91.11</td></tr><tr><td>1024</td><td>94.26</td><td>95.56</td></tr><tr><td>512</td><td>97.59</td><td>97.41</td></tr><tr><td>256</td><td>97.22</td><td>97.78</td></tr><tr><td>128</td><td>95.74</td><td>97.22</td></tr></table>


表3：不同块大小的比较。


![image](https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/6f3f2c25843b960a4035d91971b79f4cf67dd31604f42a9936c9e11910868760.jpg)



图2：不同任务检索需求分类。在信息未提供的情况下，我们根据模型的功能来区分任务。


<table><tr><td rowspan="2">Chunk Skill</td><td colspan="2">lyft_2021</td></tr><tr><td>Average Faithfulness</td><td>Average Relevancy</td></tr><tr><td>Original</td><td>95.74</td><td>95.37</td></tr><tr><td>small2big</td><td>96.67</td><td>95.37</td></tr><tr><td>sliding window</td><td>97.41</td><td>96.85</td></tr></table>


表4：不同块技能的对比。


<table><tr><td>Database</td><td>Multiple Index Type</td><td>Billion-Scale</td><td>Hybrid Search</td><td>Cloud-Native</td></tr><tr><td>Weaviate</td><td>✗</td><td>✗</td><td>√</td><td>√</td></tr><tr><td>Faiss</td><td>√</td><td>✗</td><td>✗</td><td>✗</td></tr><tr><td>Chroma</td><td>✗</td><td>✗</td><td>√</td><td>√</td></tr><tr><td>Qdrant</td><td>✗</td><td>√</td><td>√</td><td>√</td></tr><tr><td>Milvus</td><td>√</td><td>√</td><td>√</td><td>√</td></tr></table>


表6：各种向量数据库的比较


# A.3 检索方法的实验细节

不同检索方法对比实验的具体实施细节如下:

数据集 我们使用 TREC DL 2019 (Craswell et al., 2020) 和 2020 (Craswell et al., 2021) 段落排序数据集来评估不同检索方法的性能。

指标 广泛使用的检索评估指标包括 mAP、nDCG@10、R@50 和 R@1k。mAP 和 nDCG@10 都是顺序敏感的指标，会考虑搜索结果的排名。相比之下，R@k 是顺序不敏感的指标。我们还报告了每种方法每个查询的平均延迟。

实现细节 对于稀疏检索，我们使用基于TF-IDF算法的BM25算法。对于稠密检索，我们采用Contriever作为无监督对比文本编码器。基于对嵌入模型的评估，我们使用LLM-Embedder实现有监督的密集检索。我们采用Pyserini（Lin等人，2021a）中BM25和Contriever的默认实现。BM25索引在MS MARCO集合上使用Lucene构建，而密集向量索引则利用Faiss在相同数据集上以Flat配置生成。对于查询重写，我们提示Zephyr-7b-alpha $^{10}$ （一个被训练为有用助手的模型）重写原始查询。对于查询分解，我们使用GPT-3.5-turbo-0125将原始查询分解为多个子查询。我们紧密遵循HyDE（Gao等人，2022）的实现，利用更先进的指令遵循语言模型GPT-3.5-turbo-instruct生成假设答案。模型以默认温度0.7进行推理，最多采样512个标记。检索实验和评估使用Pyserini工具包进行。

# A.3.1 使用文档与查询不同拼接方式的HyDE

表8展示了不同连接策略对假设文档和查询的影响

<table><tr><td rowspan="2">Embedding Model</td><td colspan="6">namespace-Pt/msmarco</td></tr><tr><td>MRR@1</td><td>MRR@10</td><td>MRR@100</td><td>R@1</td><td>R@10</td><td>R@100</td></tr><tr><td>BAAI/LLM-Embedder(Zhang et al., 2023a)</td><td>24.79</td><td>37.58</td><td>38.62</td><td>24.07</td><td>66.45</td><td>90.75</td></tr><tr><td>BAAI/bge-base-en-v1.5(Xiao et al., 2023)</td><td>23.34</td><td>35.80</td><td>36.94</td><td>22.63</td><td>64.12</td><td>90.13</td></tr><tr><td>BAAI/bge-small-en-v1.5(Xiao et al., 2023)</td><td>23.27</td><td>35.78</td><td>36.89</td><td>22.65</td><td>63.92</td><td>89.80</td></tr><tr><td>BAAI/bge-large-en-v1.5(Xiao et al., 2023)</td><td>24.63</td><td>37.48</td><td>38.59</td><td>23.91</td><td>65.57</td><td>90.60</td></tr><tr><td>BAAI/bge-large-en(Xiao et al., 2023)</td><td>24.84</td><td>37.66</td><td>38.73</td><td>24.13</td><td>66.09</td><td>90.64</td></tr><tr><td>BAAI/bge-small-en(Xiao et al., 2023)</td><td>23.28</td><td>35.79</td><td>36.91</td><td>22.62</td><td>63.96</td><td>89.67</td></tr><tr><td>BAAI/bge-base-en(Xiao et al., 2023)</td><td>23.47</td><td>35.94</td><td>37.07</td><td>22.73</td><td>64.17</td><td>90.14</td></tr><tr><td>Alibaba-NLP/gte-large-en-v1.5(Li et al., 2023)</td><td>8.93</td><td>15.60</td><td>16.71</td><td>8.67</td><td>32.28</td><td>60.36</td></tr><tr><td>thenlper/gte-base(Li et al., 2023)</td><td>7.42</td><td>13.23</td><td>14.30</td><td>7.21</td><td>28.27</td><td>56.20</td></tr><tr><td>thenlper/gte-small(Li et al., 2023)</td><td>7.97</td><td>14.81</td><td>15.95</td><td>7.71</td><td>32.07</td><td>61.08</td></tr><tr><td>jinaai/jina-embeddings-v2-small-en(Günther et al., 2023)</td><td>8.07</td><td>15.02</td><td>16.12</td><td>7.87</td><td>32.55</td><td>60.36</td></tr><tr><td>intfloat/e5-small-v2(Wang et al., 2022)</td><td>10.04</td><td>18.23</td><td>19.41</td><td>9.74</td><td>38.92</td><td>68.42</td></tr><tr><td>intfloat/e5-large-v2(Wang et al., 2022)</td><td>9.58</td><td>17.94</td><td>19.03</td><td>9.35</td><td>39.00</td><td>66.11</td></tr><tr><td>sentence-transformers/all-mpnet-base-v2</td><td>5.80</td><td>11.26</td><td>12.26</td><td>5.66</td><td>25.57</td><td>50.94</td></tr></table>


表5：不同嵌入模型在 namespace-Pt/msmarco 上的结果。


<table><tr><td rowspan="2">Method</td><td colspan="5">TREC DL19</td><td colspan="5">TREC DL20</td></tr><tr><td>mAP</td><td>nDCG@10</td><td>R@50</td><td>R@1k</td><td>Latency</td><td>mAP</td><td>nDCG@10</td><td>R@50</td><td>R@1k</td><td>Latency</td></tr><tr><td>unsupervised</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr><td>BM25</td><td>30.13</td><td>50.58</td><td>38.32</td><td>75.01</td><td>0.07</td><td>28.56</td><td>47.96</td><td>46.18</td><td>78.63</td><td>0.29</td></tr><tr><td>Contriever</td><td>23.99</td><td>44.54</td><td>37.54</td><td>74.59</td><td>3.06</td><td>23.98</td><td>42.13</td><td>43.81</td><td>75.39</td><td>0.98</td></tr><tr><td>supervised</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr><td>LLM-Embedder</td><td>44.66</td><td>70.20</td><td>49.06</td><td>84.48</td><td>2.61</td><td>45.60</td><td>68.76</td><td>61.36</td><td>84.41</td><td>0.71</td></tr><tr><td>+ Query Rewriting</td><td>44.56</td><td>67.89</td><td>51.45</td><td>85.35</td><td>7.80</td><td>45.16</td><td>65.62</td><td>59.63</td><td>83.45</td><td>2.06</td></tr><tr><td>+ Query Decomposition</td><td>41.93</td><td>66.10</td><td>48.66</td><td>82.62</td><td>14.98</td><td>43.30</td><td>64.95</td><td>57.74</td><td>84.18</td><td>2.01</td></tr><tr><td>+ HyDE</td><td>50.87</td><td>75.44</td><td>54.93</td><td>88.76</td><td>7.21</td><td>50.94</td><td>73.94</td><td>63.80</td><td>88.03</td><td>2.14</td></tr><tr><td>+ Hybrid Search</td><td>47.14</td><td>72.50</td><td>51.13</td><td>89.08</td><td>3.20</td><td>47.72</td><td>69.80</td><td>64.32</td><td>88.04</td><td>0.77</td></tr><tr><td>+ HyDE + Hybrid Search</td><td>52.13</td><td>73.34</td><td>55.38</td><td>90.42</td><td>11.16</td><td>53.13</td><td>72.72</td><td>66.14</td><td>90.67</td><td>2.95</td></tr></table>


表7：TREC DL19/20上不同检索方法的结果。每种方法的最佳结果用粗体标出，次佳结果用下划线标出。


使用 HyDE 方法，将多个伪文档与原始查询拼接可以显著提高检索性能，但这会增加延迟，表明检索有效性（效果）与效率之间需要权衡。然而，不加选择地增加假设文档的数量并不会带来显著的好处，反而会大幅增加延迟，这说明使用单个假设文档就足够了。

# A.3.2 不同权重下稀疏检索的混合搜索

表9展示了混合搜索中不同 $\alpha$ 值的影响，其中 $\alpha$ 控制稀疏检索和密集检索组件之间的权重。相关度得分计算如下：

$$
S _ {h} = \alpha \cdot S _ {s} + S _ {d} \tag {1}
$$

其中 $S_{s}$ 、 $S_{d}$ 分别是来自稀疏检索和稠密检索的归一化相关性得分， $S_{h}$ 是总检索得分。

我们评估了五个不同的 $\alpha$ 值，以确定它们对性能的影响。结果表明， $\alpha$ 值为0.3时性能最佳，这表明适当调整 $\alpha$ 可以在一定程度上提高检索效果。

因此，我们选择了 $\alpha = 0.3$ 用于我们的检索和主要实验。

# A.4 重排序方法的实验细节

数据集 我们的实验使用了MS MARCO段落排序数据集，这是一个专为机器阅读理解任务设计的大型语料库。该数据集包含超过880万个段落和100万个查询。训练集包含约3.98亿个查询与相应正例和负例段落组成的元组，而开发集包含6,980个查询，每个查询与其BM25检索结果配对，并为每个查询保留了排名前1000的候选段落。由于测试集未公开，我们在开发集上评估方法的有效性。

指标 使用了评估指标 MRR@1、MRR@10、MRR@1k 和 Hit Rate@10。MRR@10 是 MS MARCO 提出的官方指标。

实现细节 我们遵循并修改了由PyGaggle（Nogueira et al., 2020）和TILDE提供的实现

<table><tr><td rowspan="2">Configuration</td><td colspan="5">TREC DL19</td><td colspan="5">TREC DL20</td></tr><tr><td>mAP</td><td>nDCG@10</td><td>R@50</td><td>R@1k</td><td>latency</td><td>mAP</td><td>nDCG@10</td><td>R@50</td><td>R@1k</td><td>Latency</td></tr><tr><td colspan="11">HyDE</td></tr><tr><td>w/ 1 pseudo-doc</td><td>48.77</td><td>72.49</td><td>53.20</td><td>87.73</td><td>8.08</td><td>51.31</td><td>70.37</td><td>63.28</td><td>87.81</td><td>2.09</td></tr><tr><td>w/ 1 pseudo-doc + query</td><td>50.87</td><td>75.44</td><td>54.93</td><td>88.76</td><td>7.21</td><td>50.94</td><td>73.94</td><td>63.80</td><td>88.03</td><td>2.14</td></tr><tr><td>w/ 8 pseudo-doc + query</td><td>51.64</td><td>75.12</td><td>54.51</td><td>89.17</td><td>14.15</td><td>53.14</td><td>73.65</td><td>65.79</td><td>88.67</td><td>3.44</td></tr></table>


表 8: 以不同方式拼接假设文档与查询的 HyDE


<table><tr><td rowspan="2">Hyperparameter</td><td colspan="5">TREC DL19</td><td colspan="5">TREC DL20</td></tr><tr><td>mAP</td><td>nDCG@10</td><td>R@50</td><td>R@1k</td><td>latency</td><td>mAP</td><td>nDCG@10</td><td>R@50</td><td>R@1k</td><td>Latency</td></tr><tr><td colspan="11">Hybrid Search</td></tr><tr><td><eq>\alpha = 0.1</eq></td><td>46.00</td><td>70.87</td><td>49.24</td><td>88.89</td><td>2.98</td><td>46.54</td><td>69.05</td><td>63.36</td><td>87.32</td><td>0.90</td></tr><tr><td><eq>\alpha = 0.3</eq></td><td>47.14</td><td>72.50</td><td>51.13</td><td>89.08</td><td>3.20</td><td>47.72</td><td>69.80</td><td>64.32</td><td>88.04</td><td>0.77</td></tr><tr><td><eq>\alpha = 0.5</eq></td><td>47.36</td><td>72.24</td><td>52.71</td><td>88.09</td><td>3.02</td><td>47.19</td><td>68.12</td><td>64.90</td><td>87.86</td><td>0.87</td></tr><tr><td><eq>\alpha = 0.7</eq></td><td>47.21</td><td>71.89</td><td>52.40</td><td>88.01</td><td>3.15</td><td>45.82</td><td>67.30</td><td>64.23</td><td>87.92</td><td>1.02</td></tr><tr><td><eq>\alpha = 0.9</eq></td><td>46.35</td><td>70.67</td><td>52.64</td><td>88.22</td><td>2.74</td><td>44.02</td><td>65.55</td><td>63.22</td><td>87.76</td><td>1.20</td></tr></table>


表9：不同alpha值的混合搜索结果。


(Zhuang 和 Zuccon, 2021b)。对于基于 DLM 的重排序，我们使用基于 T5-base 的 monoT5（Nogueira 等人，2020）、基于 BERT-large 的 monoBERT（Nogueira 等人，2019）以及基于 Llama-2-7b 的 RankLLaMA（Ma 等人，2023b）。对于 TILDE 重排序，我们使用基于 BERT-base 的 TILDEv2（Zhuang 和 Zuccon, 2021a）。

通常，系统会检索 50 篇文档作为重排序模块的输入。经过重排序和重打包阶段后，可通过设定 top-k 值或相关性分数阈值来进一步浓缩剩余文档。

结果分析 重排序结果如表10所示。我们将随机打乱的排序和BM25检索基线作为对比基准。所有重排序方法在所有指标上均展现出显著性能提升。monoT5与monoBERT性能大致相当，而RankLLaMA表现最优，三者处理延迟依次递增。TILDEv2速度最快，每个查询仅需约10至20毫秒，但以牺牲部分性能为代价。此外，TILDEv2要求待重排序的段落必须完全包含在先前已建索引的文档集中。对于未见过的全新段落，需在推理阶段重新进行预处理，这将抵消其效率优势。

# A.5 摘要方法的实验细节

Selective Context 通过识别并移除输入上下文中的冗余信息来提升 LLM 效率。它利用基础因果语言模型计算的自信息来评估词汇单元的信息量。

模型。该方法是非查询式的，允许在查询式和非查询式方法之间进行比较。

数据集 我们在三个数据集上评估了这些方法：Natural Questions (NQ) (Kwiatkowski et al., 2019)、TriviaQA (Joshi et al., 2017) 和 HotpotQ A (Yang et al., 2018)。

评估指标包括F1分数和摘要后更改的标记数量，以衡量简洁性。

实现细节 对于所有方法，我们使用Llama3-8B-Instruct作为生成器模型，并设置摘要比例为0.4。对于抽取式方法，重要性分数决定保留的句子。对于抽象式方法，我们通过摘要比例控制最大生成长度，以与抽取式方法保持一致。实验在NQ测试集、TriviaQA测试集和HotpotQA开发集上进行。

# A.6 生成器微调的实验细节

数据集 我们在多个问答（QA）和阅读理解数据集上微调我们的模型，包括ASQA (Stelmakh et al., 2022)、HotpotQA (Yang et al., 2018)、NarrativeQA (Kočiský et al., 2018)、NQ (Kwiatkowski et al., 2019)、SQuAD (Rajpurkar et al., 2016)、TriviaQA (Joshi et al., 2017)、TruthfulQA (Lin et al., 2021b)。我们使用它们的训练集划分（对于那些数据条目显著多于其他的数据集，我们进行了随机采样）。用于评估的数据集包括ASQA (Stelmakh et al., 2022)、HotpotQA (Yang et al., 2018)、NQ (Kwiatkowski et al., 2019)、TriviaQA (Joshi et al., 2017)。我们评估我们的

<table><tr><td rowspan="2">Method</td><td colspan="7">MS MARCO Passage ranking</td></tr><tr><td>Base Model</td><td># Params</td><td>MRR@1</td><td>MRR@10</td><td>MRR@1k</td><td>Hit Rate@10</td><td>Latency</td></tr><tr><td colspan="8">w/o Reranking</td></tr><tr><td>Random Ordering</td><td>-</td><td>-</td><td>0.011</td><td>0.027</td><td>0.068</td><td>0.092</td><td>-</td></tr><tr><td>BM25</td><td>-</td><td>-</td><td>6.52</td><td>11.65</td><td>12.59</td><td>24.63</td><td>-</td></tr><tr><td colspan="8">DLM Reranking</td></tr><tr><td>monoT5</td><td>T5-base</td><td>220M</td><td>21.62</td><td>31.78</td><td>32.40</td><td>54.07</td><td>4.5</td></tr><tr><td>monoBERT</td><td>BERT-large</td><td>340M</td><td>21.65</td><td>31.69</td><td>32.35</td><td>53.38</td><td>15.8</td></tr><tr><td>RankLLaMA</td><td>Llama-2-7b</td><td>7B</td><td>22.08</td><td>32.35</td><td>32.97</td><td>54.53</td><td>82.4</td></tr><tr><td colspan="8">TILDE Reranking</td></tr><tr><td>TILDEv2</td><td>BERT-base</td><td>110M</td><td>18.57</td><td>27.83</td><td>28.60</td><td>49.07</td><td>0.02</td></tr></table>


表10：不同重排序方法在MS MARCO段落排序数据集开发集上的结果。对于每个查询，通过BM25检索到的前1000个候选段落会被重排序。延迟以秒/查询来衡量。


<table><tr><td rowspan="2">Method</td><td colspan="2">NQ</td><td colspan="2">TQA</td><td colspan="2">HotPotQA</td><td rowspan="2">Avg.</td><td rowspan="2">Avg. Token</td></tr><tr><td>F1</td><td>#token</td><td>F1</td><td>#token</td><td>F1</td><td>#token</td></tr><tr><td>w/o Summarization</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr><td>Origin Prompt</td><td>27.07</td><td>124</td><td>33.61</td><td>152</td><td>33.92</td><td>141</td><td>31.53</td><td>139</td></tr><tr><td>Extractive Method</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr><td>BM25</td><td>27.97</td><td>40</td><td>32.44</td><td>59</td><td>28.00</td><td>63</td><td>29.47</td><td>54</td></tr><tr><td>Contriever</td><td>23.62</td><td>42</td><td>33.79</td><td>65</td><td>23.64</td><td>60</td><td>27.02</td><td>56</td></tr><tr><td>Recomp (extractive)</td><td>27.84</td><td>34</td><td>35.32</td><td>60</td><td>29.46</td><td>58</td><td>30.87</td><td>51</td></tr><tr><td>Abstractive Method</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr><td>SelectiveContext</td><td>25.05</td><td>65</td><td>34.25</td><td>70</td><td>34.43</td><td>66</td><td>31.24</td><td>67</td></tr><tr><td>LongLLMlingua</td><td>21.32</td><td>51</td><td>32.81</td><td>56</td><td>30.79</td><td>57</td><td>28.29</td><td>55</td></tr><tr><td>Recomp (abstractive)</td><td>33.68</td><td>59</td><td>35.87</td><td>61</td><td>29.01</td><td>57</td><td>32.85</td><td>59</td></tr></table>


表11：不同摘要方法的比较。


在验证集划分上进行建模，或手动从训练集中划分子集以避免重叠。每个训练集和测试集中的具体条目数详见表13。

<table><tr><td>Dataset</td><td>#Train</td><td>#Eval</td></tr><tr><td>ASQA</td><td>2,090</td><td>483</td></tr><tr><td>HotpotQA</td><td>15,000</td><td>7,405</td></tr><tr><td>TriviaQA</td><td>9,000</td><td>6,368</td></tr><tr><td>NQ</td><td>15,000</td><td>8,006</td></tr><tr><td>NarrativeQA</td><td>7,000</td><td>--</td></tr><tr><td>SQuAD</td><td>67,00</td><td>--</td></tr><tr><td>TruthfulQA</td><td>817</td><td>--</td></tr></table>


表13：各数据集中用于微调实验的示例数量


我们使用数据集提供的文档作为每个数据条目的 $d_{gold}$ 。为获得 $d_{random}$ ，我们从同一数据集内不同条目的上下文中进行采样，以确保 $d_{random}$ 和 $d_{gold}$ 的分布大致相似。

评估指标 我们采用真实覆盖率作为评估指标，考虑到问答任务的答案相对较短，而模型的生成长度有时难以限制。

实现细节 我们选择Llama-2-7b（Touvron等人，2023b）作为基础模型。为了提高效率，我们在训练期间使用LoRA（Hu等人，2021）和int8量化。提示模板

![image](https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/3c37ac172473da9904d5c2312040e61e06426b1d8c04ac443ff7028945d16f10.jpg)



图3：生成器微调的结果。


微调和评估主要遵循（Lin et al., 2023）。我们对生成器进行3个周期的训练，并将序列的最大长度限制为1600，使用批量大小为4，学习率为5e-5。在测试过程中，我们使用零样本设置。

详细结果表12展示了我们在每个数据集上的评估结果。

# A.7 综合评估的实验细节

任务与数据集 我们进行了广泛的实验，涵盖了多种NLP任务和数据集，以评估RAG系统的性能。具体而言：(1)常识推理：我们在MMLU (Hendrycks et al., 2020)、ARC-Challenge (Clark et al., 2018)和OpenbookQA (Mihaylov et al., 2018)数据集上进行了评估。(2)事实核查-

<table><tr><td>Context</td><td>Model</td><td>NQ</td><td>TriviaQA</td><td>HotpotQA</td><td>ASQA</td><td>Avg.</td></tr><tr><td rowspan="5"><eq>D_{\emptyset}</eq></td><td><eq>M_b</eq></td><td>29.78</td><td>60.44</td><td>23.73</td><td>37.89</td><td>37.96</td></tr><tr><td><eq>M_g</eq></td><td>26.23</td><td>58.26</td><td>26.67</td><td>32.30</td><td>35.87</td></tr><tr><td><eq>M_r</eq></td><td>31.10</td><td>61.37</td><td>28.40</td><td>39.96</td><td>40.21</td></tr><tr><td><eq>M_{gr}</eq></td><td>25.92</td><td>57.62</td><td>26.43</td><td>32.99</td><td>35.70</td></tr><tr><td><eq>M_{gg}</eq></td><td>26.69</td><td>58.07</td><td>27.04</td><td>33.75</td><td>36.39</td></tr><tr><td rowspan="5"><eq>D_g</eq></td><td><eq>M_b</eq></td><td>44.78</td><td>79.90</td><td>56.72</td><td>71.64</td><td>63.26</td></tr><tr><td><eq>M_g</eq></td><td>85.72</td><td>88.16</td><td>79.82</td><td>85.51</td><td>84.80</td></tr><tr><td><eq>M_r</eq></td><td>60.98</td><td>80.20</td><td>65.73</td><td>67.49</td><td>68.60</td></tr><tr><td><eq>M_{gr}</eq></td><td>87.60</td><td>87.94</td><td>81.07</td><td>87.58</td><td>86.05</td></tr><tr><td><eq>M_{gg}</eq></td><td>86.72</td><td>88.35</td><td>79.59</td><td>83.44</td><td>84.53</td></tr><tr><td rowspan="5"><eq>D_r</eq></td><td><eq>M_b</eq></td><td>16.49</td><td>50.03</td><td>21.57</td><td>28.79</td><td>29.22</td></tr><tr><td><eq>M_g</eq></td><td>22.15</td><td>46.98</td><td>24.36</td><td>29.40</td><td>30.72</td></tr><tr><td><eq>M_r</eq></td><td>36.92</td><td>58.42</td><td>29.64</td><td>39.54</td><td>41.13</td></tr><tr><td><eq>M_{gr}</eq></td><td>23.63</td><td>45.01</td><td>24.17</td><td>27.95</td><td>30.19</td></tr><tr><td><eq>M_{gg}</eq></td><td>21.08</td><td>43.83</td><td>23.23</td><td>27.33</td><td>28.87</td></tr><tr><td rowspan="5"><eq>D_{gr}</eq></td><td><eq>M_b</eq></td><td>34.65</td><td>81.27</td><td>52.75</td><td>65.42</td><td>58.52</td></tr><tr><td><eq>M_g</eq></td><td>85.00</td><td>87.33</td><td>78.18</td><td>83.02</td><td>83.38</td></tr><tr><td><eq>M_r</eq></td><td>60.28</td><td>79.32</td><td>63.82</td><td>67.29</td><td>67.68</td></tr><tr><td><eq>M_{gr}</eq></td><td>87.63</td><td>87.14</td><td>79.95</td><td>87.78</td><td>85.63</td></tr><tr><td><eq>M_{gg}</eq></td><td>86.31</td><td>86.90</td><td>78.10</td><td>83.85</td><td>83.79</td></tr></table>


表 12: 使用不同上下文增强的模型在各种 QA 数据集上的结果。


<table><tr><td>[Instruction]</td><td>Please generate ten descriptions for the continuation task.</td></tr><tr><td>[Context]</td><td>For example:1.&quot;French.Washington played a crucial role in the American Revolutionary War, leading the Continental Army against the British.&quot; Please continue writing the above paragraph.2.&quot;The discovery of the double helix structure of DNA by James Watson and Francis Crick revolutionized the field of genetics, laying the foundation for modern molecular biology and biotechnology.&quot; Please continue by discussing recent developments in genetic research, such as CRISPR gene editing, and their potential ethical implications.</td></tr></table>


表14：生成任务分类数据的模板


评估所涵盖的数据集包括：事实验证方面，采用FEVER（Thorne等，2018）与PubHealth（Zhang等，2023b）；开放域问答方面，选取NQ（Kwiatkowski等，2019）、TriviaQA（Joshi等，2017）及WebQuestions（Berant等，2013）进行评测；多跳问答方面，测试了HotPotQA（Yang等，2018）、2WikiMultiHopQA（Ho等，2020）及MuSiQue（Trivedi等，2022）数据集。针对MuSiQue，遵循Press等（2022）提出的方法，仅聚焦于可应答的2跳问题；医学问答方面，还对PubMedQA（Jin等，2019）数据集进行了评估。各数据集均从测试集中随机抽取500条样本用于实验，对于无测试集的数据集则改用开发集替代。

为了评估RAG能力，我们从NQ、TriviaQA、HotPotQA中均匀收集了共500个条目，

2WikiMultiHopQA 和 MuSiQue。每个条目都是一个“问题、黄金文档、黄金答案”三元组指标 对于开放域问答和多跳问答任务，我们使用令牌级F1分数与宽松匹配分数，其他任务则采用准确率。我们采用较为宽松的匹配标准，评估依据是模型生成结果是否包含标准答案，而非严格精确匹配（Asai等人，2023）。

针对RAG能力评估，我们采用RAGAs中的四种指标，包括忠实度、上下文相关性、答案相关性及答案正确性。忠实度衡量生成答案与检索上下文在事实层面的一致性。若答案中的所有主张均可直接从所提供上下文推断得出，则认为该答案具有忠实性。上下文相关性评估检索上下文与原始查询的相关程度。答案相关性则评价答案与

![image](https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/51ed9663385059a9927003860c4558d3516fd5d402209c262efeb5c99b7faafb.jpg)



图4：多模态检索的工作流程。上半部分展示了文本到图像的检索过程。首先，使用文本查询在数据库中找到相似度最高的图像。若找到高相似度，则直接返回该图像；否则，调用图像生成模型创建并返回合适的图像。下半部分展示了图像到文本的检索过程。用户提供的图像与数据库中的图像进行匹配，以找到相似度最高的图像。若识别到高相似度，则返回匹配图像的预存描述；否则，图像描述模型会生成并返回新的描述。


生成答案与原始查询的相关性。答案正确性涉及生成答案与真实答案相比的准确性。例如，上下文相关性是通过检索到的上下文中与回答给定问题相关的句子占所有句子的比例来计算的：

$$
\text { context   relevancy } = \frac {| S |}{| T o t a l |} \tag {2}
$$

其中 $|S|$ 表示相关句子的数量， $|Total|$ 表示检索到的句子总数。所有这些指标均使用RAGAs框架进行评估，以GPT-4作为评判标准。

此外，我们将检索到的文档与标准文档之间的余弦相似度作为检索相似度计算。检索到的文档和标准文档被输入到嵌入模型中，然后使用得到的嵌入来计算余弦相似度。

针对开放域问答和多跳问答数据集，我们将生成模型的最大新 token 数设置为 100 个 token。对于其他数据集，则设置为 50 个 token。为处理过长的检索文档，在评估 RankLLaMA 和 LongLLMLingua 时，我们将文档截断至 2048 个词。

对于所有数据集，我们在生成过程中采用贪心解码。为了更好地比较不同RAG模块的能力，我们采用零样本评估设置，即不提供上下文示例。在多项选择和事实核查任务中，模型生成的答案可能形式多样（例如，“答案是A”而非“A”）。因此，我们对模型生成的回答进行预处理，应用正则表达式模板将其与标准标签匹配。

<table><tr><td>Method</td><td>CLIP Similarity</td><td>LATENCY</td></tr><tr><td>PRO2GEN</td><td>0.266</td><td>6.64S</td></tr><tr><td>PRO2RET</td><td>0.246</td><td>0.08S</td></tr><tr><td>PRO2RET(Need retrieval)</td><td>0.258</td><td>-</td></tr><tr><td>PRO2RET(Need generation)</td><td>0.227</td><td>-</td></tr></table>

表15：文本到图像检索的结果：PRO2GEN和PRO2RET分别表示使用生成和检索方法返回图像。PRO2RET(Need retrieval)和PRO2RET(Need generation)是指使用标注为“Need retrieval”和“Need generation”的提示进行检索过程。“Need retrieval”表示检索源中存在与该提示完全匹配的精确图片。“Need generation”表示检索源中没有与该提示匹配良好的图片。检索时间显著短于生成时间，且检索质量与生成质量相当。PRO2RET(Need retrieval)的结果优于PRO2RET(Need generation)，表明扩大检索源规模可以有效改善结果。

<table><tr><td>Prompt</td><td>Result of retrieval</td><td>Result of generation</td></tr><tr><td>A tyrannosaurus</td><td><img src="https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/436f7f46c89e1db9397a05bc55f6e98768426a8023547a75d1ca011130199150.jpg"/></td><td><img src="https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/f2b85adecb5c2d1fac7da2d077a769df22f23cee33a87f3c401e0bb7e81bf4fb.jpg"/></td></tr><tr><td>A family</td><td><img src="https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/b7303f59f6bc3f9efbc833555b91bc5c8a206a2b4217d4b5ac094011f6cee720.jpg"/></td><td><img src="https://cdn-mineru.openxlab.org.cn/result/2026-06-05/39bfe668-6ef0-4d4b-82d8-45ad534ba7e3/ee1733082d9513f3939937e463dde17815bc345269a17595a53c63e9c55f2c32.jpg"/></td></tr></table>

图5：检索与生成方法的一些案例：生成模型的可控性较差，偶尔会产生错误或低质量的输出。相反，由于检索从权威参考文献中获取信息，它能始终提供高质量的结果。