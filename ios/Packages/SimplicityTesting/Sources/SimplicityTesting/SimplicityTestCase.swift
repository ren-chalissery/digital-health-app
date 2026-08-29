import Factory

/// Base for every suite that resolves anything from the container.
///
/// Factory's container is global, so a registration left behind by one test is visible to the
/// next. Resetting on both sides means a suite is unaffected by what ran before it and leaves
/// nothing for what runs after. Suites using this must be `@Suite(.serialized)`, because a shared
/// container cannot be reset safely from parallel tests.
open class SimplicityTestCase {

    public init() {
        Container.shared.reset()
    }

    deinit {
        Container.shared.reset()
    }
}
