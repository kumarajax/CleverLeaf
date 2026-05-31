const areas = [
  ["Taxonomy", "Configure curriculum editions, grades, subjects, chapters, and topics."],
  ["Question bank", "Author reviewed single-select and multiple-select questions."],
  ["AI authoring", "Generate original drafts from learning objectives with mandatory review."],
  ["Practice", "Deliver resumable batches of ten questions to invited students."],
];

export default function Home() {
  return (
    <main>
      <div className="eyebrow">ClearLeaf Phase 1</div>
      <h1>Grade 5-6 learning pilot</h1>
      <p className="lede">
        A local-first CBSE/NCERT examination preparation platform for Math and
        Science. This foundation is organized around configurable taxonomy,
        reviewed content, and future cloud portability.
      </p>
      <a className="primary-button inline-button" href="/account">Log in or sign up</a>
      <section className="cards">
        {areas.map(([title, description]) => (
          <article className="card" key={title}>
            <h2>{title}</h2>
            <p>{description}</p>
          </article>
        ))}
      </section>
    </main>
  );
}
