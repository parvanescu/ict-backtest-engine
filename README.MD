# alert-service

> ## ⚠️ Disclaimer
>
> **This application does not provide financial advice.** It is a
> technical, informational tool that displays and annotates market data
> according to a third-party trading methodology (see "Origin of the
> theory" below). Nothing it outputs — alerts, annotations, computed
> levels, or otherwise — is a recommendation to buy, sell, or hold any
> financial instrument.
>
> - No output from this application should be relied upon, or taken for
>   granted, as a basis for any trading or investment decision.
> - Trading financial instruments carries substantial risk of loss. Past
>   or hypothetical patterns do not guarantee future results.
> - This project is **not affiliated with, endorsed by, or officially
>   connected to** the creator(s) of the Quarterly Theory, Daye, ICT
>   ("Inner Circle Trader"), or any market-data provider. All trademarks and methodology names belong to their respective
>   owners and are referenced here for descriptive/technical purposes
>   only.
> - **This project was created solely for learning purposes and has no
>   financial scope.** Its developers do not use it, and do not intend to
>   use it, to earn any kind of income. The developers accept no
>   responsibility and bear no legal implication if anyone else uses this
>   software to generate money.
> - The authors and contributors accept no liability for any financial
>   loss arising from use of this software. Use it entirely at your own
>   risk.
> - This application never places, modifies, or manages trades on any
>   account. It only reads market data and computes/displays derived
>   values.
> - **This application does not supply, hold, share, or redistribute
>   market data.** If you feed it data from any source, you are solely
>   responsible for being entitled to use that data and for complying
>   with the source's terms of use (including any restrictions on
>   automated/programmatic or third-party tool access).

## Business context

Read this section at the start of every session to know what scenario we're
working in from a business point of view.

- **What it is**: An open-source (MIT-licensed) desktop application,
  currently developed in a private repository (see
  [LICENSE](./LICENSE)).
- **What it does**: Runs an algorithm over OHLCV 1-minute bars to fit
  the *Quarterly Theory* (Daye / ICT). Obtaining the market data is
  out of scope for now.
- **Origin of the theory**: The Quarterly Theory is not something we
  invented — this service is a technical implementation of an existing,
  external trading methodology (Daye's / ICT's). Do not treat business
  logic questions about the theory itself as open design decisions; they
  come from that external methodology, not from our own preference.
- **Architecture decisions that exist specifically for legal/risk
  reasons** (don't casually change these without re-checking the
  reasoning):
  - **No embedded/shared data feed.** This project does not include,
    bundle, or integrate any market-data provider; sourcing market data
    is out of scope for now. The app never acts as a middleman
    redistributing a licensed feed to many users — this avoids
    data-redistribution licensing issues entirely. If market-data
    integration is ever added, revisit this decision first.
  - **Data-only, never execution.** Only consumption of market-data
    input. No order routing, no account access, no trade
    execution or reporting — this keeps the app out of broker-dealer /
    execution-venue regulatory territory.
  - **Output stays factual, not directive.** Alerts/UI should present
    computed values (e.g. "Price 5142.25, above True Open 5138.50") and
    let the user interpret them, rather than directive language (e.g.
    "consider long"). This matters for staying clear of "personal
    recommendation" / investment-advice classification (relevant
    frameworks: MiFID II in the EU, similar concepts elsewhere).
  - **Distribution stays small and non-commercial for now.** Shared
    privately (private repo with whitelisted access, or a built zip
    handed directly to people) to a known group, for free — not published
    publicly, not monetized. If that ever changes (public release,
    payment/monetization in any form), the legal risk profile changes too
    and is worth reassessing at that point, not assumed away.

## License

MIT — see [LICENSE](./LICENSE) for full terms.

<!-- More business context to be added here. -->
