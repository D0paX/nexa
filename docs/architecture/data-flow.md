# Data Flow

## Detection flow

```text
Interface discovery
  -> subnet selection
  -> network observation
  -> normalized observation
  -> device correlation
  -> identity resolution
  -> trust verification
  -> policy evaluation
  -> security event
  -> persistence
  -> notification adapter
```

## Design requirement

Each boundary should expose explicit typed data models and validation rules.

The raw observation should remain distinguishable from the interpreted trust decision so later reviews can reconstruct why an event occurred.
