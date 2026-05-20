# Luck System Documentation

The Luck attribute is a powerful modifier that improves your chances of obtaining rare loot from blocks.

## How it Works

Luck scales your drop rates linearly. The formula is:
**`Drop Multiplier = 1 + (Luck / 100)`**

- **0 Luck:** 1.0x drops (Normal)
- **50 Luck:** 1.5x drops (+50% better)
- **100 Luck:** 2.0x drops (Double chance)

### 1. POOLED Tables (Weights)
In a pooled table (where only one item is picked), Luck increases the **weights** of items based on their **Rarity**.

**The Math:**
- `Rarity Score = log10(Total Weight / Item Weight)`
- `Weight Boost = 1 + (Luck / 100 * Rarity Score)`

This means:
- **Common items** (high weight) get **almost no boost**.
- **Rare items** (low weight) get a **massive boost**.
- **"Nothing" slots** (Air) get **zero boost**.

**Example (100 Luck):**
- `Stone: 100 weight` -> Rarity 1.1 -> Boost 1.04x -> **104 weight**
- `Diamond: 1 weight` -> Rarity 110 -> Boost 3.0x -> **3 weight** (Triple chance!)
- `Air: 10 weight` -> **10 weight** (No change)

With 100 Luck, the Diamond becomes 3x more common, while the Stone barely changes. Luck specifically targets the rarest loot!

### 2. INDEPENDENT Tables (Chances)
In an independent table (where every item rolls separately), Luck directly multiplies the success probability of every item in the list.

**Example:**
- `Diamond: 1/1000 chance` (0.1%)

With **100 Luck (2x chance)**:
- `Diamond: 2/1000 chance` (0.2%)

## Config Definition
To give an item Luck, add the `luck` attribute to its buff definition:

```yaml
buffs:
  lucky_charm:
    luck: 15.0
    sources: [ held ]
```
